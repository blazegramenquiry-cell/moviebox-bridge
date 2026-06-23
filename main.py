
import asyncio
import logging
from typing import Optional, List
from datetime import date

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from moviebox_api.v3.http_client import MovieBoxHttpClient
from moviebox_api.v3.core import (
    Search,
    SearchV2,
    Homepage,
    ItemDetails,
    DownloadableFilesDetail,
    DownloadableCaptionFileDetails,
)
from moviebox_api.v3.constants import SubjectType, ResolutionType, CustomResolutionType
from moviebox_api.v3.exceptions import (
    ZeroSearchResultsError,
    ExhaustedSearchResultsError,
)

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Initialize FastAPI app
app = FastAPI(
    title="MovieBox API Bridge",
    description="REST API bridge for moviebox-api library",
    version="1.0.0"
)

# Add CORS middleware to allow requests from your Java app
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, restrict this to your app's domain
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ============================================================================
# Pydantic Models for Request/Response
# ============================================================================

class SearchRequest(BaseModel):
    query: str
    subject_type: str = "all"  # "all", "movie", "tv_series", "music"
    page: int = 1
    per_page: int = 20


class SearchResultItem(BaseModel):
    subject_id: str
    title: str
    description: Optional[str] = None
    image: Optional[str] = None
    imdb_rate: Optional[float] = None
    subject_type: str
    release_date: Optional[str] = None


class SearchResponse(BaseModel):
    items: List[SearchResultItem]
    current_page: int
    has_more: bool
    total_count: int


class ItemDetailsResponse(BaseModel):
    subject_id: str
    title: str
    description: Optional[str] = None
    image: Optional[str] = None
    imdb_rate: Optional[float] = None
    subject_type: str
    release_date: Optional[str] = None
    duration: Optional[str] = None
    cast: Optional[List[dict]] = None
    genres: Optional[List[str]] = None


class DownloadableFile(BaseModel):
    resource_id: str
    resolution: str
    size: int
    url: str
    title: Optional[str] = None
    season: Optional[int] = None
    episode: Optional[int] = None


class DownloadableFilesResponse(BaseModel):
    subject_id: str
    title: str
    subject_type: str
    files: List[DownloadableFile]
    available_resolutions: List[str]


class CaptionFile(BaseModel):
    id: str
    language: str
    language_name: str
    url: str
    size: int


class CaptionsResponse(BaseModel):
    resource_id: str
    captions: List[CaptionFile]


# ============================================================================
# Helper Functions
# ============================================================================

def map_subject_type(subject_type_str: str) -> SubjectType:
    """Map string subject type to SubjectType enum"""
    mapping = {
        "all": SubjectType.ALL,
        "movie": SubjectType.MOVIES,
        "tv_series": SubjectType.TV_SERIES,
        "music": SubjectType.MUSIC,
    }
    return mapping.get(subject_type_str.lower(), SubjectType.ALL)


async def get_client():
    """Create and return a MovieBox HTTP client"""
    return MovieBoxHttpClient()


# ============================================================================
# API Endpoints
# ============================================================================

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {"status": "ok", "service": "MovieBox API Bridge"}


@app.post("/search")
async def search_movies(request: SearchRequest) -> SearchResponse:
    """
    Search for movies, TV series, or music.
    
    Parameters:
    - query: Search term (e.g., "Inception")
    - subject_type: Type of content ("all", "movie", "tv_series", "music")
    - page: Page number (default: 1)
    - per_page: Results per page (default: 20)
    """
    try:
        subject_type = map_subject_type(request.subject_type)
        
        async with MovieBoxHttpClient() as client:
            search_engine = Search(
                client,
                query=request.query,
                subject_type=subject_type,
                page=request.page,
                per_page=request.per_page,
            )
            
            results = await search_engine.get_content_model()
            
            # Map results to our response model
            items = []
            for item in results.items:
                items.append(
                    SearchResultItem(
                        subject_id=item.subject_id,
                        title=item.title,
                        description=item.description,
                        image=str(item.image) if item.image else None,
                        imdb_rate=item.imdb_rate,
                        subject_type=item.subject_type.value,
                        release_date=str(item.release_date) if item.release_date else None,
                    )
                )
            
            return SearchResponse(
                items=items,
                current_page=results.pager.page,
                has_more=results.pager.has_more,
                total_count=results.pager.total_count,
            )
    
    except ZeroSearchResultsError as e:
        raise HTTPException(status_code=404, detail=f"No results found: {str(e)}")
    except Exception as e:
        logger.error(f"Search error: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Search failed: {str(e)}")


@app.get("/item/{subject_id}")
async def get_item_details(subject_id: str, include_seasons: bool = False) -> ItemDetailsResponse:
    """
    Get detailed information about a specific movie or TV series.
    
    Parameters:
    - subject_id: The unique ID of the item
    - include_seasons: Include season information for TV series (default: False)
    """
    try:
        async with MovieBoxHttpClient() as client:
            details_provider = ItemDetails(client, include_seasons=include_seasons)
            item_details = await details_provider.get_content_model(subject_id)
            
            return ItemDetailsResponse(
                subject_id=item_details.subject_id,
                title=item_details.title,
                description=item_details.description,
                image=str(item_details.image) if item_details.image else None,
                imdb_rate=item_details.imdb_rate,
                subject_type=item_details.subject_type.value,
                release_date=str(item_details.release_date) if item_details.release_date else None,
                duration=item_details.duration,
                genres=item_details.genres if hasattr(item_details, 'genres') else None,
            )
    
    except Exception as e:
        logger.error(f"Details fetch error: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Failed to fetch details: {str(e)}")


@app.get("/downloadable/{subject_id}")
async def get_downloadable_files(
    subject_id: str,
    resolution: str = "best",
    page: int = 1,
    per_page: int = 20,
) -> DownloadableFilesResponse:
    """
    Get available downloadable files for a movie or TV series.
    
    Parameters:
    - subject_id: The unique ID of the item
    - resolution: Target resolution ("best", "worst", or specific like "720p", "1080p")
    - page: Page number for pagination
    - per_page: Results per page
    """
    try:
        # Map resolution string to enum
        resolution_enum = CustomResolutionType.BEST
        if resolution.lower() == "worst":
            resolution_enum = CustomResolutionType.WORST
        elif resolution.lower() in ["720p", "720"]:
            resolution_enum = ResolutionType.RES_720P
        elif resolution.lower() in ["1080p", "1080"]:
            resolution_enum = ResolutionType.RES_1080P
        elif resolution.lower() in ["480p", "480"]:
            resolution_enum = ResolutionType.RES_480P
        elif resolution.lower() in ["360p", "360"]:
            resolution_enum = ResolutionType.RES_360P
        
        async with MovieBoxHttpClient() as client:
            downloader = DownloadableFilesDetail(
                client,
                page=page,
                per_page=per_page,
                resolution=resolution_enum,
            )
            
            files_detail = await downloader.get_content_model(subject_id)
            
            # Map files to response model
            files = []
            for media_file in files_detail.list:
                files.append(
                    DownloadableFile(
                        resource_id=media_file.resource_id,
                        resolution=media_file.resolution,
                        size=media_file.size,
                        url=str(media_file.url),
                        title=media_file.title if hasattr(media_file, 'title') else None,
                        season=media_file.season if hasattr(media_file, 'season') else None,
                        episode=media_file.episode if hasattr(media_file, 'episode') else None,
                    )
                )
            
            # Get available resolutions
            available_resolutions = list(files_detail.get_quality_downloads_map().keys())
            
            return DownloadableFilesResponse(
                subject_id=subject_id,
                title=files_detail.title,
                subject_type=files_detail.subject_type.value,
                files=files,
                available_resolutions=available_resolutions,
            )
    
    except Exception as e:
        logger.error(f"Downloadable files fetch error: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Failed to fetch downloadable files: {str(e)}")


@app.get("/captions/{subject_id}/{resource_id}")
async def get_captions(subject_id: str, resource_id: str) -> CaptionsResponse:
    """
    Get available caption/subtitle files for a specific video resource.
    
    Parameters:
    - subject_id: The unique ID of the item
    - resource_id: The unique ID of the video resource
    """
    try:
        async with MovieBoxHttpClient() as client:
            caption_provider = DownloadableCaptionFileDetails(client)
            captions_detail = await caption_provider.get_content_model(subject_id, resource_id)
            
            # Map captions to response model
            captions = []
            for caption in captions_detail.list:
                captions.append(
                    CaptionFile(
                        id=caption.id,
                        language=caption.lan,
                        language_name=caption.lan_name,
                        url=str(caption.url),
                        size=caption.size,
                    )
                )
            
            return CaptionsResponse(
                resource_id=resource_id,
                captions=captions,
            )
    
    except Exception as e:
        logger.error(f"Captions fetch error: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Failed to fetch captions: {str(e)}")


@app.get("/")
async def root():
    """Root endpoint with API documentation"""
    return {
        "message": "MovieBox API Bridge",
        "version": "1.0.0",
        "endpoints": {
            "health": "/health",
            "search": "POST /search",
            "item_details": "GET /item/{subject_id}",
            "downloadable_files": "GET /downloadable/{subject_id}",
            "captions": "GET /captions/{subject_id}/{resource_id}",
            "docs": "/docs",
            "openapi": "/openapi.json",
        }
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
