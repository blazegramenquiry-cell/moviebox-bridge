package com.example.movieboxapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.example.moviebox.MovieBoxClient;
import java.util.ArrayList;
import java.util.List;

/**
 * Example Android Activity demonstrating MovieBox API Bridge integration
 */
public class MovieSearchActivity extends AppCompatActivity {
    
    private MovieBoxClient movieBoxClient;
    private EditText searchInput;
    private Button searchButton;
    private ListView resultsListView;
    private ArrayAdapter<String> adapter;
    private List<String> resultsList;
    private List<MovieBoxClient.SearchResult> searchResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_search);

        // Initialize MovieBox client (replace with your bridge URL)
        movieBoxClient = new MovieBoxClient("http://your-bridge-url:8000");

        // Initialize UI components
        searchInput = findViewById(R.id.searchInput);
        searchButton = findViewById(R.id.searchButton);
        resultsListView = findViewById(R.id.resultsListView);

        // Initialize adapter for ListView
        resultsList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, resultsList);
        resultsListView.setAdapter(adapter);

        // Check bridge health
        checkBridgeHealth();

        // Set up search button click listener
        searchButton.setOnClickListener(v -> performSearch());

        // Set up item click listener for detailed view
        resultsListView.setOnItemClickListener((parent, view, position, id) -> {
            if (searchResults != null && position < searchResults.size()) {
                MovieBoxClient.SearchResult result = searchResults.get(position);
                showItemDetails(result.subjectId);
            }
        });
    }

    /**
     * Check if the MovieBox API Bridge is accessible
     */
    private void checkBridgeHealth() {
        movieBoxClient.checkHealth(new MovieBoxClient.MovieBoxCallback() {
            @Override
            public void onSuccess(JsonObject result) {
                runOnUiThread(() -> 
                    Toast.makeText(MovieSearchActivity.this, "Bridge connected!", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> 
                    Toast.makeText(MovieSearchActivity.this, "Bridge connection failed: " + error, Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    /**
     * Perform movie search
     */
    private void performSearch() {
        String query = searchInput.getText().toString().trim();
        
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a search term", Toast.LENGTH_SHORT).show();
            return;
        }

        // Clear previous results
        resultsList.clear();
        adapter.notifyDataSetChanged();

        // Show loading message
        resultsList.add("Searching...");
        adapter.notifyDataSetChanged();

        // Perform search
        movieBoxClient.searchMovies(query, "movie", 1, new MovieBoxClient.MovieBoxCallback() {
            @Override
            public void onSuccess(JsonObject result) {
                handleSearchResults(result);
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    resultsList.clear();
                    resultsList.add("Error: " + error);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(MovieSearchActivity.this, "Search failed: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Handle search results
     */
    private void handleSearchResults(JsonObject result) {
        try {
            JsonArray items = result.getAsJsonArray("items");
            searchResults = MovieBoxClient.SearchResult.fromJson(items);

            runOnUiThread(() -> {
                resultsList.clear();
                
                if (searchResults.isEmpty()) {
                    resultsList.add("No results found");
                } else {
                    for (MovieBoxClient.SearchResult item : searchResults) {
                        String displayText = item.title + " (" + item.releaseDate + ")";
                        resultsList.add(displayText);
                    }
                }
                
                adapter.notifyDataSetChanged();
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                resultsList.clear();
                resultsList.add("Error parsing results: " + e.getMessage());
                adapter.notifyDataSetChanged();
            });
        }
    }

    /**
     * Show detailed information about an item
     */
    private void showItemDetails(String subjectId) {
        movieBoxClient.getItemDetails(subjectId, new MovieBoxClient.MovieBoxCallback() {
            @Override
            public void onSuccess(JsonObject result) {
                // You can launch a new activity or show a dialog here
                String title = result.get("title").getAsString();
                String description = result.has("description") ? result.get("description").getAsString() : "No description";
                
                runOnUiThread(() -> {
                    Toast.makeText(MovieSearchActivity.this, "Title: " + title, Toast.LENGTH_LONG).show();
                    // Launch DetailActivity with the item details
                    showDetailActivity(subjectId, title, description, result);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> 
                    Toast.makeText(MovieSearchActivity.this, "Failed to load details: " + error, Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    /**
     * Launch detail activity (you need to create this activity)
     */
    private void showDetailActivity(String subjectId, String title, String description, JsonObject details) {
        // Example: Start a new activity
        // Intent intent = new Intent(this, MovieDetailActivity.class);
        // intent.putExtra("subject_id", subjectId);
        // intent.putExtra("title", title);
        // intent.putExtra("description", description);
        // startActivity(intent);
    }
}

/**
 * Example of a Detail Activity showing downloadable files
 */
class MovieDetailActivity extends AppCompatActivity {
    
    private MovieBoxClient movieBoxClient;
    private String subjectId;
    private ListView downloadListView;
    private ArrayAdapter<String> adapter;
    private List<String> downloadList;
    private List<MovieBoxClient.DownloadableFile> downloadableFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_detail);

        movieBoxClient = new MovieBoxClient("http://your-bridge-url:8000");
        subjectId = getIntent().getStringExtra("subject_id");

        downloadListView = findViewById(R.id.downloadListView);
        downloadList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, downloadList);
        downloadListView.setAdapter(adapter);

        // Load downloadable files
        loadDownloadableFiles();

        // Set up item click listener to show captions
        downloadListView.setOnItemClickListener((parent, view, position, id) -> {
            if (downloadableFiles != null && position < downloadableFiles.size()) {
                MovieBoxClient.DownloadableFile file = downloadableFiles.get(position);
                loadCaptions(file.resourceId);
            }
        });
    }

    /**
     * Load available downloadable files
     */
    private void loadDownloadableFiles() {
        movieBoxClient.getDownloadableFiles(subjectId, "best", 1, 20, new MovieBoxClient.MovieBoxCallback() {
            @Override
            public void onSuccess(JsonObject result) {
                try {
                    JsonArray files = result.getAsJsonArray("files");
                    downloadableFiles = MovieBoxClient.DownloadableFile.fromJson(files);

                    runOnUiThread(() -> {
                        downloadList.clear();
                        
                        if (downloadableFiles.isEmpty()) {
                            downloadList.add("No files available");
                        } else {
                            for (MovieBoxClient.DownloadableFile file : downloadableFiles) {
                                String displayText = file.resolution + " - " + formatFileSize(file.size);
                                downloadList.add(displayText);
                            }
                        }
                        
                        adapter.notifyDataSetChanged();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        downloadList.clear();
                        downloadList.add("Error: " + e.getMessage());
                        adapter.notifyDataSetChanged();
                    });
                }
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    downloadList.clear();
                    downloadList.add("Error: " + error);
                    adapter.notifyDataSetChanged();
                });
            }
        });
    }

    /**
     * Load captions for a specific resource
     */
    private void loadCaptions(String resourceId) {
        movieBoxClient.getCaptions(subjectId, resourceId, new MovieBoxClient.MovieBoxCallback() {
            @Override
            public void onSuccess(JsonObject result) {
                try {
                    JsonArray captions = result.getAsJsonArray("captions");
                    List<MovieBoxClient.Caption> captionList = MovieBoxClient.Caption.fromJson(captions);

                    runOnUiThread(() -> {
                        StringBuilder captionText = new StringBuilder("Available Captions:\n");
                        for (MovieBoxClient.Caption caption : captionList) {
                            captionText.append("- ").append(caption.languageName).append("\n");
                        }
                        Toast.makeText(MovieDetailActivity.this, captionText.toString(), Toast.LENGTH_LONG).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> 
                        Toast.makeText(MovieDetailActivity.this, "Error loading captions: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> 
                    Toast.makeText(MovieDetailActivity.this, "Failed to load captions: " + error, Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    /**
     * Format file size for display
     */
    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
