package com.termux.app.ai.mcp.tools;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.termux.app.ai.mcp.MCPTool;
import com.termux.app.ai.mcp.MCPToolResult;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * MCP Tool for fetching and parsing web pages.
 */
public class WebBrowserTool implements MCPTool {
    
    private static final String TAG = "WebBrowserTool";
    private static final int MAX_CONTENT_LENGTH = 50000;
    
    private final Context context;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public WebBrowserTool(Context context) {
        this.context = context;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
    }

    @Override
    public String getName() {
        return "browse_web";
    }

    @Override
    public String getDescription() {
        return "Fetch and read the content of a web page. " +
               "Extracts main text content from HTML pages, useful for reading documentation, " +
               "articles, and other web content.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        JsonObject urlProp = new JsonObject();
        urlProp.addProperty("type", "string");
        urlProp.addProperty("description", "URL of the web page to fetch");
        properties.add("url", urlProp);
        
        JsonObject extractProp = new JsonObject();
        extractProp.addProperty("type", "string");
        extractProp.addProperty("description", "What to extract: text, links, or all");
        JsonArray extractEnum = new JsonArray();
        extractEnum.add("text");
        extractEnum.add("links");
        extractEnum.add("all");
        extractProp.add("enum", extractEnum);
        properties.add("extract", extractProp);
        
        schema.add("properties", properties);
        
        JsonArray required = new JsonArray();
        required.add("url");
        schema.add("required", required);
        
        return schema;
    }

    @Override
    public MCPToolResult execute(JsonObject input) {
        try {
            String url = input.get("url").getAsString();
            String extractType = input.has("extract") ? input.get("extract").getAsString() : "text";
            
            // Validate URL
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            
            // Fetch the page
            Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.5")
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return MCPToolResult.error("Failed to fetch page: HTTP " + response.code());
                }
                
                String contentType = response.header("Content-Type", "");
                String html = response.body().string();
                
                // Truncate if too long
                if (html.length() > MAX_CONTENT_LENGTH) {
                    html = html.substring(0, MAX_CONTENT_LENGTH);
                }
                
                StringBuilder result = new StringBuilder();
                result.append("URL: ").append(url).append("\n\n");
                
                // Extract title
                String title = extractTitle(html);
                if (title != null) {
                    result.append("Title: ").append(title).append("\n\n");
                }
                
                switch (extractType) {
                    case "links":
                        result.append(extractLinks(html));
                        break;
                    case "all":
                        result.append("--- Content ---\n");
                        result.append(extractText(html));
                        result.append("\n\n--- Links ---\n");
                        result.append(extractLinks(html));
                        break;
                    case "text":
                    default:
                        result.append(extractText(html));
                        break;
                }
                
                JsonObject data = new JsonObject();
                data.addProperty("url", url);
                data.addProperty("title", title);
                
                return MCPToolResult.success(result.toString(), data);
            }
            
        } catch (IOException e) {
            return MCPToolResult.error("Network error: " + e.getMessage());
        } catch (Exception e) {
            return MCPToolResult.error("Browse error: " + e.getMessage());
        }
    }

    @Override
    public String getCategory() {
        return "web";
    }

    /**
     * Extract the page title from HTML.
     */
    private String extractTitle(String html) {
        Pattern pattern = Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return decodeHtmlEntities(matcher.group(1).trim());
        }
        return null;
    }

    /**
     * Extract main text content from HTML.
     */
    private String extractText(String html) {
        // Remove script and style elements
        html = html.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
        html = html.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
        html = html.replaceAll("(?is)<noscript[^>]*>.*?</noscript>", " ");
        html = html.replaceAll("(?is)<!--.*?-->", " ");
        
        // Try to extract main content areas
        String mainContent = extractMainContent(html);
        if (mainContent != null && !mainContent.isEmpty()) {
            html = mainContent;
        }
        
        // Convert block elements to newlines
        html = html.replaceAll("(?i)</(p|div|h[1-6]|li|tr|br|hr)[^>]*>", "\n");
        html = html.replaceAll("(?i)<(p|div|h[1-6]|li|tr|br|hr)[^>]*>", "\n");
        
        // Remove remaining tags
        html = html.replaceAll("<[^>]+>", " ");
        
        // Decode HTML entities
        html = decodeHtmlEntities(html);
        
        // Clean up whitespace
        html = html.replaceAll("[ \\t]+", " ");
        html = html.replaceAll("\\n[ \\t]+", "\n");
        html = html.replaceAll("[ \\t]+\\n", "\n");
        html = html.replaceAll("\\n{3,}", "\n\n");
        
        return html.trim();
    }

    /**
     * Try to extract the main content area.
     */
    private String extractMainContent(String html) {
        // Try common main content selectors
        String[] patterns = {
            "(?is)<article[^>]*>(.*?)</article>",
            "(?is)<main[^>]*>(.*?)</main>",
            "(?is)<div[^>]*class=\"[^\"]*content[^\"]*\"[^>]*>(.*?)</div>",
            "(?is)<div[^>]*id=\"content\"[^>]*>(.*?)</div>",
            "(?is)<div[^>]*class=\"[^\"]*post[^\"]*\"[^>]*>(.*?)</div>"
        };
        
        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        // Fall back to body
        Pattern bodyPattern = Pattern.compile("(?is)<body[^>]*>(.*?)</body>");
        Matcher bodyMatcher = bodyPattern.matcher(html);
        if (bodyMatcher.find()) {
            return bodyMatcher.group(1);
        }
        
        return null;
    }

    /**
     * Extract links from HTML.
     */
    private String extractLinks(String html) {
        StringBuilder links = new StringBuilder();
        Pattern pattern = Pattern.compile("<a[^>]*href=\"([^\"]+)\"[^>]*>([^<]*)</a>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        
        int count = 0;
        while (matcher.find() && count < 50) {
            String href = matcher.group(1);
            String text = decodeHtmlEntities(matcher.group(2).trim());
            
            // Skip empty or javascript links
            if (href.startsWith("javascript:") || href.equals("#") || text.isEmpty()) {
                continue;
            }
            
            links.append("- ").append(text);
            if (!href.equals(text)) {
                links.append(" (").append(href).append(")");
            }
            links.append("\n");
            count++;
        }
        
        if (links.length() == 0) {
            return "No links found.\n";
        }
        
        return links.toString();
    }

    /**
     * Decode common HTML entities.
     */
    private String decodeHtmlEntities(String text) {
        if (text == null) return null;
        
        text = text.replace("&nbsp;", " ");
        text = text.replace("&amp;", "&");
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&quot;", "\"");
        text = text.replace("&#39;", "'");
        text = text.replace("&apos;", "'");
        text = text.replace("&mdash;", "—");
        text = text.replace("&ndash;", "–");
        text = text.replace("&bull;", "•");
        text = text.replace("&hellip;", "...");
        text = text.replace("&copy;", "©");
        text = text.replace("&reg;", "®");
        
        // Decode numeric entities
        Pattern numericPattern = Pattern.compile("&#(\\d+);");
        Matcher matcher = numericPattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            int code = Integer.parseInt(matcher.group(1));
            matcher.appendReplacement(sb, String.valueOf((char) code));
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
}
