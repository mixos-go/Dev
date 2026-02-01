package com.termux.app.ai.mcp.tools;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.termux.app.ai.mcp.MCPTool;
import com.termux.app.ai.mcp.MCPToolResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MCP Tool for managing Termux packages.
 */
public class PackageManagerTool implements MCPTool {
    
    private static final String TAG = "PackageManagerTool";
    private static final String TERMUX_PREFIX = "/data/data/com.termux/files/usr";
    
    private final Context context;

    public PackageManagerTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "package_manager";
    }

    @Override
    public String getDescription() {
        return "Manage Termux packages: install, uninstall, update, and list packages. " +
               "Uses pkg command which is a wrapper for apt.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        JsonObject actionProp = new JsonObject();
        actionProp.addProperty("type", "string");
        actionProp.addProperty("description", "Action to perform");
        JsonArray actionEnum = new JsonArray();
        actionEnum.add("install");
        actionEnum.add("uninstall");
        actionEnum.add("update");
        actionEnum.add("upgrade");
        actionEnum.add("list");
        actionEnum.add("search");
        actionEnum.add("info");
        actionProp.add("enum", actionEnum);
        properties.add("action", actionProp);
        
        JsonObject packagesProp = new JsonObject();
        packagesProp.addProperty("type", "array");
        packagesProp.addProperty("description", "Package names (for install/uninstall/info)");
        properties.add("packages", packagesProp);
        
        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description", "Search query (for search action)");
        properties.add("query", queryProp);
        
        schema.add("properties", properties);
        
        JsonArray required = new JsonArray();
        required.add("action");
        schema.add("required", required);
        
        return schema;
    }

    @Override
    public MCPToolResult execute(JsonObject input) {
        try {
            String action = input.get("action").getAsString().toLowerCase();
            
            switch (action) {
                case "install":
                    return installPackages(input);
                case "uninstall":
                    return uninstallPackages(input);
                case "update":
                    return updatePackageList();
                case "upgrade":
                    return upgradePackages();
                case "list":
                    return listPackages();
                case "search":
                    return searchPackages(input);
                case "info":
                    return packageInfo(input);
                default:
                    return MCPToolResult.error("Unknown action: " + action);
            }
            
        } catch (Exception e) {
            return MCPToolResult.error("Package operation failed: " + e.getMessage());
        }
    }

    @Override
    public boolean requiresConfirmation() {
        return true; // Package operations should be confirmed
    }

    @Override
    public String getCategory() {
        return "system";
    }

    private MCPToolResult installPackages(JsonObject input) {
        List<String> packages = getPackageList(input);
        if (packages.isEmpty()) {
            return MCPToolResult.error("No packages specified for installation");
        }
        
        String packageList = String.join(" ", packages);
        return executeCommand("pkg install -y " + packageList, 
            "Installing: " + packageList);
    }

    private MCPToolResult uninstallPackages(JsonObject input) {
        List<String> packages = getPackageList(input);
        if (packages.isEmpty()) {
            return MCPToolResult.error("No packages specified for uninstallation");
        }
        
        String packageList = String.join(" ", packages);
        return executeCommand("pkg uninstall -y " + packageList,
            "Uninstalling: " + packageList);
    }

    private MCPToolResult updatePackageList() {
        return executeCommand("pkg update -y", "Updating package list...");
    }

    private MCPToolResult upgradePackages() {
        return executeCommand("pkg upgrade -y", "Upgrading all packages...");
    }

    private MCPToolResult listPackages() {
        MCPToolResult result = executeCommand("pkg list-installed", "Listing installed packages...");
        
        if (result.isSuccess()) {
            String content = result.getContent();
            // Parse and format the output
            String[] lines = content.split("\n");
            StringBuilder formatted = new StringBuilder();
            formatted.append("Installed packages:\n\n");
            
            int count = 0;
            for (String line : lines) {
                if (line.contains("/") && !line.startsWith("Listing")) {
                    // Format: package/stable version arch
                    String[] parts = line.split("/");
                    if (parts.length >= 1) {
                        formatted.append("- ").append(parts[0]).append("\n");
                        count++;
                    }
                }
            }
            
            formatted.append("\nTotal: ").append(count).append(" packages");
            
            JsonObject data = new JsonObject();
            data.addProperty("count", count);
            
            return MCPToolResult.success(formatted.toString(), data);
        }
        
        return result;
    }

    private MCPToolResult searchPackages(JsonObject input) {
        String query = input.has("query") ? input.get("query").getAsString() : "";
        if (query.isEmpty()) {
            return MCPToolResult.error("Search query is required");
        }
        
        return executeCommand("pkg search " + query, "Searching for: " + query);
    }

    private MCPToolResult packageInfo(JsonObject input) {
        List<String> packages = getPackageList(input);
        if (packages.isEmpty()) {
            return MCPToolResult.error("No package specified for info");
        }
        
        String pkg = packages.get(0);
        return executeCommand("pkg show " + pkg, "Getting info for: " + pkg);
    }

    private List<String> getPackageList(JsonObject input) {
        List<String> packages = new ArrayList<>();
        
        if (input.has("packages") && input.get("packages").isJsonArray()) {
            JsonArray arr = input.getAsJsonArray("packages");
            for (int i = 0; i < arr.size(); i++) {
                packages.add(arr.get(i).getAsString());
            }
        }
        
        return packages;
    }

    private MCPToolResult executeCommand(String command, String progressMessage) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.environment().put("HOME", TERMUX_PREFIX.replace("/usr", "/home"));
            pb.environment().put("PREFIX", TERMUX_PREFIX);
            pb.environment().put("PATH", TERMUX_PREFIX + "/bin:" + System.getenv("PATH"));
            pb.environment().put("TMPDIR", TERMUX_PREFIX + "/tmp");
            pb.environment().put("LANG", "en_US.UTF-8");
            pb.environment().put("TERM", "xterm-256color");
            
            pb.command("/system/bin/sh", "-c", command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            boolean completed = process.waitFor(120, TimeUnit.SECONDS);
            
            if (!completed) {
                process.destroyForcibly();
                return MCPToolResult.error("Command timed out");
            }
            
            int exitCode = process.exitValue();
            String outputStr = output.toString().trim();
            
            JsonObject data = new JsonObject();
            data.addProperty("exit_code", exitCode);
            data.addProperty("command", command);
            
            if (exitCode == 0) {
                return MCPToolResult.success(outputStr.isEmpty() ? 
                    "Operation completed successfully" : outputStr, data);
            } else {
                return MCPToolResult.error("Command failed with exit code " + exitCode + 
                    (outputStr.isEmpty() ? "" : ":\n" + outputStr));
            }
            
        } catch (Exception e) {
            return MCPToolResult.error("Failed to execute: " + e.getMessage());
        }
    }
}
