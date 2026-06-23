# MovieBox API Bridge

A **FastAPI-based REST API bridge** that allows Java/Android applications to interact with the Moviebox service through simple HTTP requests. This bridge wraps the powerful `moviebox-api` Python library and exposes its functionality as clean JSON APIs.

## Features

✅ **Search Movies & TV Series** - Search for content by title, genre, or other criteria  
✅ **Get Item Details** - Retrieve comprehensive information about movies/shows including cast, ratings, and descriptions  
✅ **List Downloadable Files** - Get available download links with different resolutions  
✅ **Fetch Subtitles** - Retrieve available subtitle/caption files for videos  
✅ **Pagination Support** - Navigate through large result sets efficiently  
✅ **Async/Await** - Built on FastAPI for high performance and concurrency  
✅ **Docker Ready** - Easy deployment with Docker and Docker Compose  
✅ **CORS Enabled** - Works seamlessly with frontend applications  
✅ **Interactive Docs** - Swagger UI at `/docs` for API exploration  

## Quick Start

### Local Development

```bash
# Clone or download the bridge
cd moviebox-bridge

# Create virtual environment
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Run the server
python main.py

# Visit http://localhost:8000/docs for interactive API documentation
```

### Docker Deployment

```bash
# Using Docker Compose (easiest)
docker-compose up -d

# Or manual Docker
docker build -t moviebox-bridge:latest .
docker run -d -p 8000:8000 moviebox-bridge:latest

# Test the bridge
curl http://localhost:8000/health
```

## API Endpoints

### 1. Health Check
```bash
GET /health
```

### 2. Search
```bash
POST /search
Content-Type: application/json

{
  "query": "Inception",
  "subject_type": "movie",
  "page": 1,
  "per_page": 20
}
```

### 3. Get Item Details
```bash
GET /item/{subject_id}?include_seasons=false
```

### 4. Get Downloadable Files
```bash
GET /downloadable/{subject_id}?resolution=best&page=1&per_page=20
```

### 5. Get Captions
```bash
GET /captions/{subject_id}/{resource_id}
```

## Java/Android Integration

### Using the Provided Client Library

```java
// Initialize client
MovieBoxClient client = new MovieBoxClient("http://your-bridge-url:8000");

// Search for movies
client.searchMovies("Inception", "movie", 1, new MovieBoxClient.MovieBoxCallback() {
    @Override
    public void onSuccess(JsonObject result) {
        // Handle results
        JsonArray items = result.getAsJsonArray("items");
        List<MovieBoxClient.SearchResult> results = MovieBoxClient.SearchResult.fromJson(items);
    }

    @Override
    public void onError(String error) {
        // Handle error
    }
});

// Get item details
client.getItemDetails("12345", new MovieBoxClient.MovieBoxCallback() {
    @Override
    public void onSuccess(JsonObject result) {
        String title = result.get("title").getAsString();
    }

    @Override
    public void onError(String error) {
        // Handle error
    }
});

// Get downloadable files
client.getDownloadableFiles("12345", "best", 1, 20, new MovieBoxClient.MovieBoxCallback() {
    @Override
    public void onSuccess(JsonObject result) {
        JsonArray files = result.getAsJsonArray("files");
        List<MovieBoxClient.DownloadableFile> downloadables = MovieBoxClient.DownloadableFile.fromJson(files);
    }

    @Override
    public void onError(String error) {
        // Handle error
    }
});
```

### Add to Your Android Project

1. Copy `MovieBoxClient.java` to your Android project's `src/main/java/com/example/moviebox/` directory
2. Add OkHttp and Gson dependencies to `build.gradle`:
   ```gradle
   dependencies {
       implementation 'com.squareup.okhttp3:okhttp:4.11.0'
       implementation 'com.google.code.gson:gson:2.10.1'
   }
   ```
3. Use the client as shown in the examples above

### Example Android Activity

See `ExampleAndroidActivity.java` for a complete example of how to integrate the bridge into an Android app.

## Deployment

### Deploy to a VPS (DigitalOcean, Linode, AWS EC2, etc.)

See `SETUP_GUIDE.md` for detailed deployment instructions including:
- SSH setup
- Docker installation
- Nginx reverse proxy configuration
- SSL/HTTPS setup with Let's Encrypt
- Load balancing for high traffic

### Deploy to Cloud Platforms

- **Heroku**: Push the Docker image to Heroku Container Registry
- **AWS**: Use ECS or Elastic Beanstalk with the Docker image
- **Google Cloud**: Deploy to Cloud Run or App Engine
- **Azure**: Use Container Instances or App Service

## Configuration

### Environment Variables

```bash
# Optional: Custom auth token (if default is blocked)
MOVIEBOX_V3_AUTH_TOKEN=your-token-here

# Server configuration
HOST=0.0.0.0
PORT=8000
```

### CORS Configuration

By default, CORS is enabled for all origins. To restrict in production:

```python
# In main.py
app.add_middleware(
    CORSMiddleware,
    allow_origins=["your-app-domain.com"],  # Restrict to your domain
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

## Performance Optimization

### Enable Response Caching

```bash
pip install fastapi-cache2 redis
```

Then add caching decorators to endpoints:
```python
from fastapi_cache2.decorators import cache

@app.get("/item/{subject_id}")
@cache(expire=3600)  # Cache for 1 hour
async def get_item_details(subject_id: str):
    # ...
```

### Load Balancing

Deploy multiple instances behind a load balancer:
- Nginx
- HAProxy
- Cloud provider load balancer (AWS ALB, Google Cloud Load Balancer, etc.)

## Troubleshooting

### Connection Refused
- Ensure the bridge is running: `docker ps` or check if `python main.py` is still running
- Check if the port is correct (default: 8000)
- If on a remote server, ensure firewall allows the port

### No Results Found
- Try a different search query
- Check if the Moviebox service is accessible from your server
- Review logs: `docker logs moviebox-bridge`

### Slow Response Times
- The Moviebox service may be geographically distant
- Consider adding caching for frequently accessed items
- Increase timeout values if needed

### Invalid Token
- The Moviebox service may have revoked the embedded auth token
- Update the `moviebox-api` library: `pip install --upgrade moviebox-api`
- Or set a custom token: `MOVIEBOX_V3_AUTH_TOKEN=your-token`

## Security

- **HTTPS**: Always use HTTPS in production with SSL certificates
- **CORS**: Restrict CORS to your app's domain in production
- **Rate Limiting**: Add rate limiting to prevent abuse
- **Authentication**: Consider adding API key authentication to the bridge

## Support

For issues with:
- **The Bridge**: Check logs and refer to `SETUP_GUIDE.md`
- **The Underlying Library**: Visit https://github.com/Simatwa/moviebox-api
- **Java Client**: Review `MovieBoxClient.java` and `ExampleAndroidActivity.java`

## License

This bridge is provided as-is for educational and personal use. The underlying `moviebox-api` library is licensed under the Unlicense.

## Disclaimer

This project is an unofficial wrapper and is not affiliated with Moviebox. Use responsibly and respect copyright laws in your jurisdiction.

---

**Ready to get started?** See `SETUP_GUIDE.md` for comprehensive setup and deployment instructions.
