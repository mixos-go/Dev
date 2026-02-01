package com.termux.app.ai.mcp.tools;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.termux.app.ai.mcp.MCPTool;
import com.termux.app.ai.mcp.MCPToolResult;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * MCP Tool for file system operations.
 */
public class FileOperationTool implements MCPTool {
    
    private static final String TAG = "FileOperationTool";
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    
    private final Context context;

    public FileOperationTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "file_operation";
    }

    @Override
    public String getDescription() {
        return "Perform file system operations: create, read, write, edit, delete, " +
               "copy, move files and directories. Supports text files up to 10MB.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        // Operation type
        JsonObject operationProp = new JsonObject();
        operationProp.addProperty("type", "string");
        operationProp.addProperty("description", "Operation to perform: create, read, write, append, delete, copy, move, mkdir, list, exists");
        JsonArray operationEnum = new JsonArray();
        operationEnum.add("create");
        operationEnum.add("read");
        operationEnum.add("write");
        operationEnum.add("append");
        operationEnum.add("delete");
        operationEnum.add("copy");
        operationEnum.add("move");
        operationEnum.add("mkdir");
        operationEnum.add("list");
        operationEnum.add("exists");
        operationProp.add("enum", operationEnum);
        properties.add("operation", operationProp);
        
        // Path
        JsonObject pathProp = new JsonObject();
        pathProp.addProperty("type", "string");
        pathProp.addProperty("description", "File or directory path");
        properties.add("path", pathProp);
        
        // Content (for write operations)
        JsonObject contentProp = new JsonObject();
        contentProp.addProperty("type", "string");
        contentProp.addProperty("description", "Content to write (for create/write/append operations)");
        properties.add("content", contentProp);
        
        // Destination (for copy/move)
        JsonObject destProp = new JsonObject();
        destProp.addProperty("type", "string");
        destProp.addProperty("description", "Destination path (for copy/move operations)");
        properties.add("destination", destProp);
        
        schema.add("properties", properties);
        
        JsonArray required = new JsonArray();
        required.add("operation");
        required.add("path");
        schema.add("required", required);
        
        return schema;
    }

    @Override
    public MCPToolResult execute(JsonObject input) {
        try {
            String operation = input.get("operation").getAsString().toLowerCase();
            String path = input.get("path").getAsString();
            
            // Resolve path relative to home if not absolute
            if (!path.startsWith("/")) {
                path = TERMUX_HOME + "/" + path;
            }
            
            // Security check
            if (!isPathAllowed(path)) {
                return MCPToolResult.error("Access denied: Path outside allowed directories");
            }
            
            switch (operation) {
                case "create":
                case "write":
                    return writeFile(path, input.has("content") ? 
                        input.get("content").getAsString() : "", false);
                        
                case "append":
                    return writeFile(path, input.has("content") ? 
                        input.get("content").getAsString() : "", true);
                        
                case "read":
                    return readFile(path);
                    
                case "delete":
                    return deleteFile(path);
                    
                case "copy":
                    if (!input.has("destination")) {
                        return MCPToolResult.error("Copy operation requires 'destination' parameter");
                    }
                    return copyFile(path, resolvePath(input.get("destination").getAsString()));
                    
                case "move":
                    if (!input.has("destination")) {
                        return MCPToolResult.error("Move operation requires 'destination' parameter");
                    }
                    return moveFile(path, resolvePath(input.get("destination").getAsString()));
                    
                case "mkdir":
                    return createDirectory(path);
                    
                case "list":
                    return listDirectory(path);
                    
                case "exists":
                    return checkExists(path);
                    
                default:
                    return MCPToolResult.error("Unknown operation: " + operation);
            }
            
        } catch (Exception e) {
            return MCPToolResult.error("File operation failed: " + e.getMessage());
        }
    }

    @Override
    public String getCategory() {
        return "filesystem";
    }

    private MCPToolResult writeFile(String path, String content, boolean append) {
        try {
            File file = new File(path);
            
            // Create parent directories if needed
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, append))) {
                writer.write(content);
            }
            
            String action = append ? "appended to" : (file.exists() ? "updated" : "created");
            JsonObject data = new JsonObject();
            data.addProperty("path", path);
            data.addProperty("size", content.length());
            
            return MCPToolResult.success("File " + action + ": " + path, data);
            
        } catch (IOException e) {
            return MCPToolResult.error("Failed to write file: " + e.getMessage());
        }
    }

    private MCPToolResult readFile(String path) {
        try {
            File file = new File(path);
            
            if (!file.exists()) {
                return MCPToolResult.error("File not found: " + path);
            }
            
            if (!file.isFile()) {
                return MCPToolResult.error("Not a file: " + path);
            }
            
            if (file.length() > MAX_FILE_SIZE) {
                return MCPToolResult.error("File too large (max " + (MAX_FILE_SIZE / 1024 / 1024) + "MB)");
            }
            
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            
            JsonObject data = new JsonObject();
            data.addProperty("path", path);
            data.addProperty("size", file.length());
            
            return MCPToolResult.success(content.toString(), data);
            
        } catch (IOException e) {
            return MCPToolResult.error("Failed to read file: " + e.getMessage());
        }
    }

    private MCPToolResult deleteFile(String path) {
        try {
            File file = new File(path);
            
            if (!file.exists()) {
                return MCPToolResult.error("File not found: " + path);
            }
            
            boolean deleted;
            if (file.isDirectory()) {
                deleted = deleteRecursive(file);
            } else {
                deleted = file.delete();
            }
            
            if (deleted) {
                return MCPToolResult.success("Deleted: " + path);
            } else {
                return MCPToolResult.error("Failed to delete: " + path);
            }
            
        } catch (Exception e) {
            return MCPToolResult.error("Delete failed: " + e.getMessage());
        }
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    private MCPToolResult copyFile(String source, String destination) {
        try {
            File srcFile = new File(source);
            File destFile = new File(destination);
            
            if (!srcFile.exists()) {
                return MCPToolResult.error("Source not found: " + source);
            }
            
            // Create parent directories
            File parent = destFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            if (srcFile.isDirectory()) {
                copyDirectory(srcFile, destFile);
            } else {
                Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            
            return MCPToolResult.success("Copied " + source + " to " + destination);
            
        } catch (IOException e) {
            return MCPToolResult.error("Copy failed: " + e.getMessage());
        }
    }

    private void copyDirectory(File source, File dest) throws IOException {
        if (!dest.exists()) {
            dest.mkdirs();
        }
        
        File[] files = source.listFiles();
        if (files != null) {
            for (File file : files) {
                File destFile = new File(dest, file.getName());
                if (file.isDirectory()) {
                    copyDirectory(file, destFile);
                } else {
                    Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private MCPToolResult moveFile(String source, String destination) {
        try {
            File srcFile = new File(source);
            File destFile = new File(destination);
            
            if (!srcFile.exists()) {
                return MCPToolResult.error("Source not found: " + source);
            }
            
            // Create parent directories
            File parent = destFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            Files.move(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            return MCPToolResult.success("Moved " + source + " to " + destination);
            
        } catch (IOException e) {
            return MCPToolResult.error("Move failed: " + e.getMessage());
        }
    }

    private MCPToolResult createDirectory(String path) {
        try {
            File dir = new File(path);
            
            if (dir.exists()) {
                if (dir.isDirectory()) {
                    return MCPToolResult.success("Directory already exists: " + path);
                } else {
                    return MCPToolResult.error("Path exists but is not a directory: " + path);
                }
            }
            
            if (dir.mkdirs()) {
                return MCPToolResult.success("Created directory: " + path);
            } else {
                return MCPToolResult.error("Failed to create directory: " + path);
            }
            
        } catch (Exception e) {
            return MCPToolResult.error("mkdir failed: " + e.getMessage());
        }
    }

    private MCPToolResult listDirectory(String path) {
        try {
            File dir = new File(path);
            
            if (!dir.exists()) {
                return MCPToolResult.error("Directory not found: " + path);
            }
            
            if (!dir.isDirectory()) {
                return MCPToolResult.error("Not a directory: " + path);
            }
            
            File[] files = dir.listFiles();
            if (files == null) {
                return MCPToolResult.error("Cannot list directory: " + path);
            }
            
            StringBuilder listing = new StringBuilder();
            listing.append("Contents of ").append(path).append(":\n\n");
            
            // Separate directories and files
            java.util.List<File> dirs = new java.util.ArrayList<>();
            java.util.List<File> regularFiles = new java.util.ArrayList<>();
            
            for (File file : files) {
                if (file.isDirectory()) {
                    dirs.add(file);
                } else {
                    regularFiles.add(file);
                }
            }
            
            // Sort alphabetically
            java.util.Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            java.util.Collections.sort(regularFiles, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            
            // List directories first
            for (File file : dirs) {
                listing.append("[DIR]  ").append(file.getName()).append("/\n");
            }
            
            // Then files
            for (File file : regularFiles) {
                listing.append("[FILE] ").append(file.getName())
                       .append(" (").append(formatSize(file.length())).append(")\n");
            }
            
            if (files.length == 0) {
                listing.append("(empty directory)\n");
            }
            
            JsonObject data = new JsonObject();
            data.addProperty("path", path);
            data.addProperty("count", files.length);
            
            return MCPToolResult.success(listing.toString(), data);
            
        } catch (Exception e) {
            return MCPToolResult.error("List failed: " + e.getMessage());
        }
    }

    private MCPToolResult checkExists(String path) {
        File file = new File(path);
        boolean exists = file.exists();
        
        JsonObject data = new JsonObject();
        data.addProperty("path", path);
        data.addProperty("exists", exists);
        
        if (exists) {
            data.addProperty("is_file", file.isFile());
            data.addProperty("is_directory", file.isDirectory());
            data.addProperty("size", file.length());
            data.addProperty("readable", file.canRead());
            data.addProperty("writable", file.canWrite());
        }
        
        String result = exists ? 
            (file.isDirectory() ? "Directory exists: " : "File exists: ") + path :
            "Does not exist: " + path;
            
        return MCPToolResult.success(result, data);
    }

    private String resolvePath(String path) {
        if (!path.startsWith("/")) {
            return TERMUX_HOME + "/" + path;
        }
        return path;
    }

    private boolean isPathAllowed(String path) {
        // Allow paths within Termux home and common safe directories
        String[] allowedPrefixes = {
            TERMUX_HOME,
            "/data/data/com.termux/files/usr",
            "/sdcard",
            "/storage/emulated/0"
        };
        
        for (String prefix : allowedPrefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        
        return false;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
