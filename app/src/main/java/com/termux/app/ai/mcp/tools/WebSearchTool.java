package com.termux.app.ai.mcp.tools;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.termux.app.ai.mcp.MCPTool;
import com.termux.app.ai.mcp.MCPToolResult;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * MCP Tool for web search using Tavily API.
 */
public class WebSearchTool implements MCPTool {
    
    private static final String TAG = "WebSearchTool";
    private static final String TAVILY_API_URL = "https://api.tavily.com/search";
    private static final String PREFS_NAME = "ai_settings";
    
    private final Context context;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private String tavilyApiKey;

    public WebSearchTool(Context context) {
        this.context = context;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
        
        loadSettings();
    }

    private void loadSettings() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        tavilyApiKey = prefs.getString("tavily_api_key", "");
    }

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "Search the web using Tavily API to find current information, " +
               "documentation, tutorials, and answers to questions. " +
               "Returns relevant search results with snippets.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description", "Search query");
        properties.add("query", queryProp);
        
        JsonObject maxResultsProp = new JsonObject();
        maxResultsProp.addProperty("type", "integer");
        maxResultsProp.addProperty("description", "Maximum number of results (default: 5)");
        properties.add("max_results", maxResultsProp);
        
        JsonObject searchDepthProp = new JsonObject();
        searchDepthProp.addProperty("type", "string");
        searchDepthProp.addProperty("description", "Search depth: basic or advanced");
        JsonArray depthEnum = new JsonArray();
        depthEnum.add("basic");
        depthEnum.add("advanced");
        searchDepthProp.add("enum", depthEnum);
        properties.add("search_depth", searchDepthProp);
        
        JsonObject includeDomainsProp = new JsonObject();
        includeDomainsProp.addProperty("type", "array");
        includeDomainsProp.addProperty("description", "Only include results from these domains");
        properties.add("include_domains", includeDomainsProp);
        
        schema.add("properties", properties);
        
        JsonArray required = new JsonArray();
        required.add("query");
        schema.add("required", required);
        
        return schema;
    }

    @Override
    public MCPToolResult execute(JsonObject input) {
        // Reload settings in case API key was updated
        loadSettings();
        
        if (tavilyApiKey == null || tavilyApiKey.isEmpty()) {
            return MCPToolResult.error("Tavily API key not configured. Please add your API key in Settings.");
        }
        
        try {
            String query = input.get("query").getAsString();
            int maxResults = input.has("max_results") ? input.get("max_results").getAsInt() : 5;
            String searchDepth = input.has("search_depth") ? input.get("search_depth").getAsString() : "basic";
            
            // Build request body
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("api_key", tavilyApiKey);
            requestBody.addProperty("query", query);
            requestBody.addProperty("max_results", maxResults);
            requestBody.addProperty("search_depth", searchDepth);
            requestBody.addProperty("include_answer", true);
            requestBody.addProperty("include_raw_content", false);
            
            if (input.has("include_domains") && input.get("include_domains").isJsonArray()) {
                requestBody.add("include_domains", input.getAsJsonArray("include_domains"));
            }
            
            RequestBody body = RequestBody.create(
                gson.toJson(requestBody),
                MediaType.parse("application/json")
            );
            
            Request request = new Request.Builder()
                .url(TAVILY_API_URL)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    return MCPToolResult.error("Search failed: " + response.code() + " " + errorBody);
                }
                
                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                
                return formatSearchResults(query, jsonResponse);
            }
            
        } catch (IOException e) {
            return MCPToolResult.error("Search request failed: " + e.getMessage());
        } catch (Exception e) {
            return MCPToolResult.error("Search error: " + e.getMessage());
        }
    }

    private MCPToolResult formatSearchResults(String query, JsonObject response) {
        StringBuilder result = new StringBuilder();
        result.append("Search results for: \"").append(query).append("\"\n\n");
        
        // Include AI-generated answer if available
        if (response.has("answer") && !response.get("answer").isJsonNull()) {
            String answer = response.get("answer").getAsString();
            result.append("Summary:\n").append(answer).append("\n\n");
            result.append("---\n\n");
        }
        
        // Format individual results
        if (response.has("results") && response.get("results").isJsonArray()) {
            JsonArray results = response.getAsJsonArray("results");
            
            for (int i = 0; i < results.size(); i++) {
                JsonObject item = results.get(i).getAsJsonObject();
                
                String title = item.has("title") ? item.get("title").getAsString() : "Untitled";
                String url = item.has("url") ? item.get("url").getAsString() : "";
                String content = item.has("content") ? item.get("content").getAsString() : "";
                
                result.append(i + 1).append(". ").append(title).append("\n");
                result.append("   URL: ").append(url).append("\n");
                
                if (!content.isEmpty()) {
                    // Truncate long content
                    if (content.length() > 300) {
                        content = content.substring(0, 300) + "...";
                    }
                    result.append("   ").append(content).append("\n");
                }
                
                result.append("\n");
            }
            
            if (results.size() == 0) {
                result.append("No results found.\n");
            }
        }
        
        JsonObject data = new JsonObject();
        data.addProperty("query", query);
        if (response.has("results")) {
            data.addProperty("result_count", response.getAsJsonArray("results").size());
        }
        
        return MCPToolResult.success(result.toString(), data);
    }

    @Override
    public String getCategory() {
        return "web";
    }

    @Override
    public boolean isAvailable() {
        loadSettings();
        return tavilyApiKey != null && !tavilyApiKey.isEmpty();
    }
}
