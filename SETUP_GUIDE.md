## Overview

This FastAPI bridge acts as a REST API intermediary between your Java/Android application and the Moviebox service. Your Java app communicates with this bridge using standard HTTP requests, and the bridge handles all the complex cryptographic signing and data extraction.

## Architecture

```
┌─────────────────────┐
│   Java/Android      │
│   Application       │
└──────────┬──────────┘
           │ HTTP/JSON
           ↓
┌─────────────────────┐
│   FastAPI Bridge    │
│   (This Server)     │
└──────────┬──────────┘
           │ Async HTTP
           ↓
┌─────────────────────┐
│   Moviebox API      │
│   (v3)              │
└─────────────────────┘
```

---

## Installation & Local Testing

### Prerequisites

- Python 3.12+
- pip (Python package manager)
- Git

### Step 1: Clone or Download the Bridge

```bash
cd /home/ubuntu/moviebox-bridge
```

### Step 2: Create a Virtual Environment (Recommended)

```bash
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
```

### Step 3: Install Dependencies

```bash
pip install -r requirements.txt
```

### Step 4: Run the Server Locally

```bash
python main.py
```

You should see output like:
```
INFO:     Uvicorn running on http://0.0.0.0:8000
```

### Step 5: Test the API

Open your browser and visit:
- **Health Check**: http://localhost:8000/health
- **API Documentation**: http://localhost:8000/docs (Interactive Swagger UI)
- **OpenAPI Schema**: http://localhost:8000/openapi.json

---

## Docker Deployment (Recommended for Production)

### Option 1: Using Docker Compose (Easiest)

```bash
docker-compose up -d
```

This will:
- Build the Docker image
- Start the container
- Expose the API on `http://localhost:8000`

### Option 2: Manual Docker Build

```bash
docker build -t moviebox-bridge:latest .
docker run -d -p 8000:8000 --name moviebox-bridge moviebox-bridge:latest
```

### Verify Docker Container

```bash
curl http://localhost:8000/health
```

---

## Deployment to Cloud (VPS/Cloud Server)

### Option 1: Deploy to a Linux VPS (DigitalOcean, Linode, AWS EC2, etc.)

1. **SSH into your server**:
   ```bash
   ssh root@your-server-ip
   ```

2. **Install Docker and Docker Compose**:
   ```bash
   curl -fsSL https://get.docker.com -o get-docker.sh
   sh get-docker.sh
   sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
   sudo chmod +x /usr/local/bin/docker-compose
   ```

3. **Clone the bridge repository**:
   ```bash
   git clone <your-repo-url> moviebox-bridge
   cd moviebox-bridge
   ```

4. **Deploy with Docker Compose**:
   ```bash
   docker-compose up -d
   ```

5. **Set up a reverse proxy (Nginx)**:
   ```bash
   sudo apt-get install nginx
   ```
   
   Create `/etc/nginx/sites-available/moviebox-bridge`:
   ```nginx
   server {
       listen 80;
       server_name your-domain.com;

       location / {
           proxy_pass http://localhost:8000;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
   }
   ```

   Enable the site:
   ```bash
   sudo ln -s /etc/nginx/sites-available/moviebox-bridge /etc/nginx/sites-enabled/
   sudo nginx -t
   sudo systemctl restart nginx
   ```

6. **Set up SSL (Let's Encrypt)**:
   ```bash
   sudo apt-get install certbot python3-certbot-nginx
   sudo certbot --nginx -d your-domain.com
   ```

---

## API Endpoints Reference

### 1. Health Check
```
GET /health
```
**Response**:
```json
{
  "status": "ok",
  "service": "MovieBox API Bridge"
}
```

### 2. Search for Movies/TV Series
```
POST /search
```
**Request Body**:
```json
{
  "query": "Inception",
  "subject_type": "movie",
  "page": 1,
  "per_page": 20
}
```
**Response**:
```json
{
  "items": [
    {
      "subject_id": "12345",
      "title": "Inception",
      "description": "A skilled thief...",
      "image": "https://...",
      "imdb_rate": 8.8,
      "subject_type": "movie",
      "release_date": "2010-07-16"
    }
  ],
  "current_page": 1,
  "has_more": false,
  "total_count": 1
}
```

### 3. Get Item Details
```
GET /item/{subject_id}?include_seasons=false
```
**Response**:
```json
{
  "subject_id": "12345",
  "title": "Inception",
  "description": "...",
  "image": "https://...",
  "imdb_rate": 8.8,
  "subject_type": "movie",
  "release_date": "2010-07-16",
  "duration": "148 min"
}
```

### 4. Get Downloadable Files
```
GET /downloadable/{subject_id}?resolution=best&page=1&per_page=20
```
**Response**:
```json
{
  "subject_id": "12345",
  "title": "Inception",
  "subject_type": "movie",
  "files": [
    {
      "resource_id": "res-001",
      "resolution": "1080p",
      "size": 2147483648,
      "url": "https://download-link.com/file.mp4",
      "title": "Inception - 1080p"
    }
  ],
  "available_resolutions": ["720p", "1080p", "480p"]
}
```

### 5. Get Captions/Subtitles
```
GET /captions/{subject_id}/{resource_id}
```
**Response**:
```json
{
  "resource_id": "res-001",
  "captions": [
    {
      "id": "cap-001",
      "language": "en",
      "language_name": "English",
      "url": "https://subtitle-link.com/file.srt",
      "size": 65536
    },
    {
      "id": "cap-002",
      "language": "es",
      "language_name": "Spanish",
      "url": "https://subtitle-link.com/file-es.srt",
      "size": 61440
    }
  ]
}
```

---

## Java/Android Client Integration

### Using OkHttp (Recommended for Android)

Add to your `build.gradle`:
```gradle
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.11.0'
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

### Example: Search for Movies

```java
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class MovieBoxClient {
    private static final String BASE_URL = "http://your-bridge-url:8000";
    private OkHttpClient client = new OkHttpClient();
    private Gson gson = new Gson();

    public void searchMovies(String query, String subjectType, int page) {
        // Build request body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("query", query);
        requestBody.addProperty("subject_type", subjectType);
        requestBody.addProperty("page", page);
        requestBody.addProperty("per_page", 20);

        RequestBody body = RequestBody.create(
            requestBody.toString(),
            MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
            .url(BASE_URL + "/search")
            .post(body)
            .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                String responseBody = response.body().string();
                JsonObject result = gson.fromJson(responseBody, JsonObject.class);
                
                // Handle the search results
                System.out.println("Search results: " + result);
            }
        });
    }

    public void getItemDetails(String subjectId) {
        Request request = new Request.Builder()
            .url(BASE_URL + "/item/" + subjectId)
            .get()
            .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                String responseBody = response.body().string();
                JsonObject result = gson.fromJson(responseBody, JsonObject.class);
                
                // Handle the item details
                System.out.println("Item details: " + result);
            }
        });
    }

    public void getDownloadableFiles(String subjectId, String resolution) {
        String url = BASE_URL + "/downloadable/" + subjectId + "?resolution=" + resolution;
        
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                String responseBody = response.body().string();
                JsonObject result = gson.fromJson(responseBody, JsonObject.class);
                
                // Handle the downloadable files
                System.out.println("Downloadable files: " + result);
            }
        });
    }
}
```

### Using Retrofit (Alternative)

Add to your `build.gradle`:
```gradle
dependencies {
    implementation 'com.squareup.retrofit2:retrofit:2.10.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.10.0'
}
```

Create the API interface:
```java
import retrofit2.Call;
import retrofit2.http.*;
import com.google.gson.JsonObject;

public interface MovieBoxAPI {
    @POST("/search")
    Call<JsonObject> search(@Body JsonObject request);

    @GET("/item/{subject_id}")
    Call<JsonObject> getItemDetails(@Path("subject_id") String subjectId);

    @GET("/downloadable/{subject_id}")
    Call<JsonObject> getDownloadableFiles(
        @Path("subject_id") String subjectId,
        @Query("resolution") String resolution
    );

    @GET("/captions/{subject_id}/{resource_id}")
    Call<JsonObject> getCaptions(
        @Path("subject_id") String subjectId,
        @Path("resource_id") String resourceId
    );
}
```

Initialize Retrofit:
```java
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

Retrofit retrofit = new Retrofit.Builder()
    .baseUrl("http://your-bridge-url:8000")
    .addConverterFactory(GsonConverterFactory.create())
    .build();

MovieBoxAPI api = retrofit.create(MovieBoxAPI.class);

// Use the API
JsonObject searchRequest = new JsonObject();
searchRequest.addProperty("query", "Inception");
searchRequest.addProperty("subject_type", "movie");

api.search(searchRequest).enqueue(new Callback<JsonObject>() {
    @Override
    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
        if (response.isSuccessful()) {
            JsonObject result = response.body();
            // Handle results
        }
    }

    @Override
    public void onFailure(Call<JsonObject> call, Throwable t) {
        t.printStackTrace();
    }
});
```

---

## Troubleshooting

### Issue: "Connection refused" or "Cannot reach server"
- Ensure the bridge is running: `docker ps` or check if `python main.py` is still running
- Check if the port is correct (default: 8000)
- If deployed on a remote server, ensure firewall allows port 8000 or 80/443

### Issue: "401 Unauthorized" or "Invalid token"
- The Moviebox service may have revoked the embedded auth token
- Update the `moviebox-api` library: `pip install --upgrade moviebox-api`
- Or set a custom token via environment variable: `MOVIEBOX_V3_AUTH_TOKEN=your-token`

### Issue: "No results found" or "Empty search results"
- Try a different search query
- Check if the Moviebox service is accessible from your server
- Review logs: `docker logs moviebox-bridge` (if using Docker)

### Issue: Slow response times
- The Moviebox service may be slow or geographically distant
- Consider adding caching to the bridge for frequently searched items
- Increase timeout values if needed

---

## Performance Optimization

### Add Response Caching

You can add caching to frequently accessed endpoints using `fastapi-cache2`:

```bash
pip install fastapi-cache2 redis
```

Then update `main.py`:
```python
from fastapi_cache2 import FastAPICache
from fastapi_cache2.backends.redis import RedisBackend
from fastapi_cache2.decorators import cache

@app.get("/item/{subject_id}")
@cache(expire=3600)  # Cache for 1 hour
async def get_item_details(subject_id: str):
    # ... existing code
```

### Load Balancing

If you expect high traffic, deploy multiple instances of the bridge behind a load balancer (Nginx, HAProxy, or cloud provider load balancer).

---

## Security Considerations

1. **HTTPS**: Always use HTTPS in production. Set up SSL certificates using Let's Encrypt.
2. **CORS**: The bridge currently allows all origins. Restrict this in production:
   ```python
   allow_origins=["your-app-domain.com"]
   ```
3. **Rate Limiting**: Add rate limiting to prevent abuse:
   ```bash
   pip install slowapi
   ```
4. **Authentication**: Consider adding API key authentication to the bridge itself.

---

## Support & Issues

For issues with the bridge itself, check the logs:
```bash
# Local
tail -f /tmp/moviebox-bridge.log

# Docker
docker logs -f moviebox-bridge
```

For issues with the underlying `moviebox-api` library, visit: https://github.com/Simatwa/moviebox-api
