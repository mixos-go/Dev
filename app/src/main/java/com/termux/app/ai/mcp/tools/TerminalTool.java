package com.termux.app.ai.mcp.tools;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.termux.app.ai.mcp.MCPTool;
import com.termux.app.ai.mcp.MCPToolResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * MCP Tool for executing terminal commands in Termux environment.
 */
public class TerminalTool implements MCPTool {
    
    private static final String TAG = "TerminalTool";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final String TERMUX_PREFIX = "/data/data/com.termux/files/usr";
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    
    private final Context context;
    private String workingDirectory;
    private int timeoutSeconds;

    public TerminalTool(Context context) {
        this.context = context;
        this.workingDirectory = TERMUX_HOME;
        this.timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    }

    @Override
    public String getName() {
        return "execute_command";
    }

    @Override
    public String getDescription() {
        return "Execute a shell command in the Termux terminal environment. " +
               "Can run any command available in Termux including package management (pkg), " +
               "file operations, git, python, node, and other installed tools.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        JsonObject commandProp = new JsonObject();
        commandProp.addProperty("type", "string");
        commandProp.addProperty("description", "The shell command to execute");
        properties.add("command", commandProp);
        
        JsonObject workdirProp = new JsonObject();
        workdirProp.addProperty("type", "string");
        workdirProp.addProperty("description", "Working directory for the command (optional)");
        properties.add("working_directory", workdirProp);
        
        JsonObject timeoutProp = new JsonObject();
        timeoutProp.addProperty("type", "integer");
        timeoutProp.addProperty("description", "Timeout in seconds (default: 30)");
        properties.add("timeout", timeoutProp);
        
        schema.add("properties", properties);
        
        JsonArray required = new JsonArray();
        required.add("command");
        schema.add("required", required);
        
        return schema;
    }

    @Override
    public MCPToolResult execute(JsonObject input) {
        try {
            String command = input.get("command").getAsString();
            
            // Get optional working directory
            String workDir = workingDirectory;
            if (input.has("working_directory") && !input.get("working_directory").isJsonNull()) {
                workDir = input.get("working_directory").getAsString();
            }
            
            // Get optional timeout
            int timeout = timeoutSeconds;
            if (input.has("timeout") && !input.get("timeout").isJsonNull()) {
                timeout = input.get("timeout").getAsInt();
            }
            
            // Check for dangerous commands
            if (isDangerousCommand(command)) {
                return MCPToolResult.requiresConfirmation(
                    "This command may be destructive: " + command + "\nDo you want to proceed?"
                );
            }
            
            // Build process
            ProcessBuilder pb = new ProcessBuilder();
            pb.directory(new File(workDir));
            
            // Set up Termux environment
            pb.environment().put("HOME", TERMUX_HOME);
            pb.environment().put("PREFIX", TERMUX_PREFIX);
            pb.environment().put("PATH", TERMUX_PREFIX + "/bin:" + System.getenv("PATH"));
            pb.environment().put("TMPDIR", TERMUX_PREFIX + "/tmp");
            pb.environment().put("LANG", "en_US.UTF-8");
            pb.environment().put("TERM", "xterm-256color");
            
            // Use sh to execute the command
            pb.command("/system/bin/sh", "-c", command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // Wait for completion with timeout
            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
            
            if (!completed) {
                process.destroyForcibly();
                return MCPToolResult.error("Command timed out after " + timeout + " seconds");
            }
            
            int exitCode = process.exitValue();
            String outputStr = output.toString().trim();
            
            // Build result with metadata
            JsonObject data = new JsonObject();
            data.addProperty("exit_code", exitCode);
            data.addProperty("command", command);
            data.addProperty("working_directory", workDir);
            
            if (exitCode == 0) {
                String resultContent = outputStr.isEmpty() ? 
                    "Command executed successfully (no output)" : outputStr;
                return MCPToolResult.success(resultContent, data);
            } else {
                return MCPToolResult.error("Command failed with exit code " + exitCode + 
                    (outputStr.isEmpty() ? "" : ":\n" + outputStr));
            }
            
        } catch (Exception e) {
            return MCPToolResult.error("Failed to execute command: " + e.getMessage());
        }
    }

    @Override
    public boolean requiresConfirmation() {
        return false; // Handled per-command
    }

    @Override
    public String getCategory() {
        return "terminal";
    }

    /**
     * Check if a command is potentially dangerous.
     */
    private boolean isDangerousCommand(String command) {
        String lower = command.toLowerCase().trim();
        
        // Dangerous patterns
        String[] dangerousPatterns = {
            "rm -rf /",
            "rm -rf ~",
            "rm -rf $HOME",
            "> /dev/sd",
            "mkfs.",
            "dd if=",
            ":(){:|:&};:",  // Fork bomb
            "chmod -R 777 /",
            "chown -R",
            "wget.*|.*sh",
            "curl.*|.*sh",
            "pkg uninstall termux"
        };
        
        for (String pattern : dangerousPatterns) {
            if (lower.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Set the default working directory.
     */
    public void setWorkingDirectory(String directory) {
        this.workingDirectory = directory;
    }

    /**
     * Set the default timeout.
     */
    public void setTimeoutSeconds(int seconds) {
        this.timeoutSeconds = seconds;
    }
}
