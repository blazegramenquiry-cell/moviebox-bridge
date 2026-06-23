package com.example.moviebox;

import okhttp3.*;
import com.google.gson.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Java Client for MovieBox API Bridge
 * 
 * Usage:
 * MovieBoxClient client = new MovieBoxClient("http://your-bridge-url:8000");
 * client.searchMovies("Inception", "movie", 1, new MovieBoxCallback() {
 *     @Override
 *     public void onSuccess(JsonObject result) {
 *         // Handle results
 *     }
 *     
 *     @Override
 *     public void onError(String error) {
 *         // Handle error
 *     }
 * });
 */
public class MovieBoxClient {
    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final Gson gson;

    /**
     * Constructor
     * @param baseUrl Base URL of the MovieBox API Bridge (e.g., "http://localhost:8000")
     */
    public MovieBoxClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/$", ""); // Remove trailing slash
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
    }

    /**
     * Callback interface for async operations
     */
    public interface MovieBoxCallback {
        void onSuccess(JsonObject result);
        void onError(String error);
    }

    /**
     * Check if the bridge is healthy
     */
    public void checkHealth(MovieBoxCallback callback) {
        Request request = new Request.Builder()
            .url(baseUrl + "/health")
            .get()
            .build();

        executeRequest(request, callback);
    }

    /**
     * Search for movies, TV series, or music
     * 
     * @param query Search term (e.g., "Inception")
     * @param subjectType Type of content: "all", "movie", "tv_series", "music"
     * @param page Page number (default: 1)
     * @param callback Callback for handling results
     */
    public void searchMovies(String query, String subjectType, int page, MovieBoxCallback callback) {
        searchMovies(query, subjectType, page, 20, callback);
    }

    /**
     * Search for movies, TV series, or music with custom per_page
     */
    public void searchMovies(String query, String subjectType, int page, int perPage, MovieBoxCallback callback) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("query", query);
        requestBody.addProperty("subject_type", subjectType);
        requestBody.addProperty("page", page);
        requestBody.addProperty("per_page", perPage);

        RequestBody body = RequestBody.create(
            requestBody.toString(),
            MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
            .url(baseUrl + "/search")
            .post(body)
            .build();

        executeRequest(request, callback);
    }

    /**
     * Get detailed information about a specific item
     * 
     * @param subjectId The unique ID of the item
     * @param callback Callback for handling results
     */
    public void getItemDetails(String subjectId, MovieBoxCallback callback) {
        getItemDetails(subjectId, false, callback);
    }

    /**
     * Get detailed information about a specific item with optional season info
     */
    public void getItemDetails(String subjectId, boolean includeSeasons, MovieBoxCallback callback) {
        String url = baseUrl + "/item/" + subjectId + "?include_seasons=" + includeSeasons;
        
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        executeRequest(request, callback);
    }

    /**
     * Get available downloadable files for an item
     * 
     * @param subjectId The unique ID of the item
     * @param callback Callback for handling results
     */
    public void getDownloadableFiles(String subjectId, MovieBoxCallback callback) {
        getDownloadableFiles(subjectId, "best", 1, 20, callback);
    }

    /**
     * Get available downloadable files with custom parameters
     * 
     * @param subjectId The unique ID of the item
     * @param resolution Target resolution ("best", "worst", "720p", "1080p", etc.)
     * @param page Page number for pagination
     * @param perPage Results per page
     * @param callback Callback for handling results
     */
    public void getDownloadableFiles(String subjectId, String resolution, int page, int perPage, MovieBoxCallback callback) {
        String url = String.format(
            "%s/downloadable/%s?resolution=%s&page=%d&per_page=%d",
            baseUrl, subjectId, resolution, page, perPage
        );
        
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        executeRequest(request, callback);
    }

    /**
     * Get available captions/subtitles for a video resource
     * 
     * @param subjectId The unique ID of the item
     * @param resourceId The unique ID of the video resource
     * @param callback Callback for handling results
     */
    public void getCaptions(String subjectId, String resourceId, MovieBoxCallback callback) {
        String url = String.format(
            "%s/captions/%s/%s",
            baseUrl, subjectId, resourceId
        );
        
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        executeRequest(request, callback);
    }

    /**
     * Execute an HTTP request asynchronously
     */
    private void executeRequest(Request request, MovieBoxCallback callback) {
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        callback.onError("HTTP " + response.code() + ": " + errorBody);
                        return;
                    }

                    String responseBody = response.body().string();
                    JsonObject result = JsonParser.parseString(responseBody).getAsJsonObject();
                    callback.onSuccess(result);
                } catch (Exception e) {
                    callback.onError("Parse error: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Helper class to parse search results
     */
    public static class SearchResult {
        public String subjectId;
        public String title;
        public String description;
        public String image;
        public double imdbRate;
        public String subjectType;
        public String releaseDate;

        public static List<SearchResult> fromJson(JsonArray items) {
            List<SearchResult> results = new ArrayList<>();
            for (JsonElement element : items) {
                JsonObject obj = element.getAsJsonObject();
                SearchResult result = new SearchResult();
                result.subjectId = obj.get("subject_id").getAsString();
                result.title = obj.get("title").getAsString();
                result.description = obj.has("description") ? obj.get("description").getAsString() : "";
                result.image = obj.has("image") ? obj.get("image").getAsString() : "";
                result.imdbRate = obj.has("imdb_rate") ? obj.get("imdb_rate").getAsDouble() : 0;
                result.subjectType = obj.get("subject_type").getAsString();
                result.releaseDate = obj.has("release_date") ? obj.get("release_date").getAsString() : "";
                results.add(result);
            }
            return results;
        }
    }

    /**
     * Helper class to parse downloadable files
     */
    public static class DownloadableFile {
        public String resourceId;
        public String resolution;
        public long size;
        public String url;
        public String title;
        public Integer season;
        public Integer episode;

        public static List<DownloadableFile> fromJson(JsonArray files) {
            List<DownloadableFile> results = new ArrayList<>();
            for (JsonElement element : files) {
                JsonObject obj = element.getAsJsonObject();
                DownloadableFile file = new DownloadableFile();
                file.resourceId = obj.get("resource_id").getAsString();
                file.resolution = obj.get("resolution").getAsString();
                file.size = obj.get("size").getAsLong();
                file.url = obj.get("url").getAsString();
                file.title = obj.has("title") ? obj.get("title").getAsString() : "";
                file.season = obj.has("season") ? obj.get("season").getAsInt() : null;
                file.episode = obj.has("episode") ? obj.get("episode").getAsInt() : null;
                results.add(file);
            }
            return results;
        }
    }

    /**
     * Helper class to parse captions
     */
    public static class Caption {
        public String id;
        public String language;
        public String languageName;
        public String url;
        public long size;

        public static List<Caption> fromJson(JsonArray captions) {
            List<Caption> results = new ArrayList<>();
            for (JsonElement element : captions) {
                JsonObject obj = element.getAsJsonObject();
                Caption caption = new Caption();
                caption.id = obj.get("id").getAsString();
                caption.language = obj.get("language").getAsString();
                caption.languageName = obj.get("language_name").getAsString();
                caption.url = obj.get("url").getAsString();
                caption.size = obj.get("size").getAsLong();
                results.add(caption);
            }
            return results;
        }
    }
              }
          
