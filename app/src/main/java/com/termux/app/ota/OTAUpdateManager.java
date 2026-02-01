package com.termux.app.ota;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Manager for OTA (Over-The-Air) updates from GitHub releases.
 */
public class OTAUpdateManager {
    
    private static final String TAG = "OTAUpdateManager";
    private static final String PREFS_NAME = "ota_settings";
    private static final String PREF_GITHUB_OWNER = "github_owner";
    private static final String PREF_GITHUB_REPO = "github_repo";
    private static final String PREF_LAST_CHECK = "last_check_timestamp";
    private static final String PREF_SKIP_VERSION = "skip_version";
    private static final String PREF_AUTO_CHECK = "auto_check_enabled";
    
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final long CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000; // 24 hours
    
    private final Context context;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final DownloadManager downloadManager;
    
    private String githubOwner;
    private String githubRepo;
    private long downloadId = -1;
    private UpdateCallback callback;
    private BroadcastReceiver downloadReceiver;

    public interface UpdateCallback {
        void onUpdateAvailable(UpdateInfo updateInfo);
        void onNoUpdateAvailable();
        void onError(String error);
        void onDownloadProgress(int progress);
        void onDownloadComplete(File apkFile);
    }

    public OTAUpdateManager(Context context) {
        this.context = context;
        this.gson = new Gson();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
        
        loadSettings();
        registerDownloadReceiver();
    }

    private void loadSettings() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        githubOwner = prefs.getString(PREF_GITHUB_OWNER, "");
        githubRepo = prefs.getString(PREF_GITHUB_REPO, "");
    }

    /**
     * Configure the GitHub repository for updates.
     */
    public void setGitHubRepository(String owner, String repo) {
        this.githubOwner = owner;
        this.githubRepo = repo;
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(PREF_GITHUB_OWNER, owner)
            .putString(PREF_GITHUB_REPO, repo)
            .apply();
    }

    /**
     * Check for updates from GitHub releases.
     */
    public void checkForUpdates(UpdateCallback callback) {
        this.callback = callback;
        
        if (githubOwner.isEmpty() || githubRepo.isEmpty()) {
            mainHandler.post(() -> callback.onError("GitHub repository not configured"));
            return;
        }
        
        executor.execute(() -> {
            try {
                String url = GITHUB_API_BASE + "/repos/" + githubOwner + "/" + githubRepo + "/releases/latest";
                
                Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .addHeader("User-Agent", "TermuxAI-UpdateChecker")
                    .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        if (response.code() == 404) {
                            mainHandler.post(() -> callback.onNoUpdateAvailable());
                        } else {
                            mainHandler.post(() -> callback.onError("GitHub API error: " + response.code()));
                        }
                        return;
                    }
                    
                    String body = response.body().string();
                    JsonObject release = gson.fromJson(body, JsonObject.class);
                    
                    UpdateInfo updateInfo = parseRelease(release);
                    
                    if (updateInfo != null && isNewerVersion(updateInfo.getVersion())) {
                        // Check if user has skipped this version
                        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        String skipVersion = prefs.getString(PREF_SKIP_VERSION, "");
                        
                        if (updateInfo.getVersion().equals(skipVersion)) {
                            mainHandler.post(() -> callback.onNoUpdateAvailable());
                        } else {
                            mainHandler.post(() -> callback.onUpdateAvailable(updateInfo));
                        }
                    } else {
                        mainHandler.post(() -> callback.onNoUpdateAvailable());
                    }
                    
                    // Update last check timestamp
                    prefs.edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply();
                }
                
            } catch (IOException e) {
                Log.e(TAG, "Error checking for updates", e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "Error parsing update info", e);
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        });
    }

    /**
     * Check all releases (not just latest) for pre-releases, etc.
     */
    public void checkAllReleases(UpdateCallback callback, boolean includePrerelease) {
        this.callback = callback;
        
        if (githubOwner.isEmpty() || githubRepo.isEmpty()) {
            mainHandler.post(() -> callback.onError("GitHub repository not configured"));
            return;
        }
        
        executor.execute(() -> {
            try {
                String url = GITHUB_API_BASE + "/repos/" + githubOwner + "/" + githubRepo + "/releases";
                
                Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .addHeader("User-Agent", "TermuxAI-UpdateChecker")
                    .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onError("GitHub API error: " + response.code()));
                        return;
                    }
                    
                    String body = response.body().string();
                    JsonArray releases = gson.fromJson(body, JsonArray.class);
                    
                    for (int i = 0; i < releases.size(); i++) {
                        JsonObject release = releases.get(i).getAsJsonObject();
                        
                        boolean isPrerelease = release.has("prerelease") && 
                            release.get("prerelease").getAsBoolean();
                        
                        if (!includePrerelease && isPrerelease) {
                            continue;
                        }
                        
                        UpdateInfo updateInfo = parseRelease(release);
                        
                        if (updateInfo != null && isNewerVersion(updateInfo.getVersion())) {
                            mainHandler.post(() -> callback.onUpdateAvailable(updateInfo));
                            return;
                        }
                    }
                    
                    mainHandler.post(() -> callback.onNoUpdateAvailable());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error checking releases", e);
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        });
    }

    /**
     * Download an update APK.
     */
    public void downloadUpdate(UpdateInfo updateInfo) {
        if (updateInfo.getDownloadUrl() == null || updateInfo.getDownloadUrl().isEmpty()) {
            if (callback != null) {
                callback.onError("No download URL available");
            }
            return;
        }
        
        try {
            // Clean up old APKs
            cleanupOldApks();
            
            Uri downloadUri = Uri.parse(updateInfo.getDownloadUrl());
            
            DownloadManager.Request request = new DownloadManager.Request(downloadUri);
            request.setTitle("Termux AI Update");
            request.setDescription("Downloading version " + updateInfo.getVersion());
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, 
                "termux-ai-" + updateInfo.getVersion() + ".apk");
            request.setMimeType("application/vnd.android.package-archive");
            
            downloadId = downloadManager.enqueue(request);
            
            // Start progress monitoring
            monitorDownloadProgress();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting download", e);
            if (callback != null) {
                callback.onError("Download failed: " + e.getMessage());
            }
        }
    }

    /**
     * Install a downloaded APK.
     */
    public void installUpdate(File apkFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(context, 
                    context.getPackageName() + ".fileprovider", apkFile);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }
            
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            context.startActivity(intent);
            
        } catch (Exception e) {
            Log.e(TAG, "Error installing update", e);
            if (callback != null) {
                callback.onError("Installation failed: " + e.getMessage());
            }
        }
    }

    /**
     * Skip a specific version (don't prompt again).
     */
    public void skipVersion(String version) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_SKIP_VERSION, version).apply();
    }

    /**
     * Check if automatic update checking is enabled.
     */
    public boolean isAutoCheckEnabled() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_AUTO_CHECK, true);
    }

    /**
     * Enable or disable automatic update checking.
     */
    public void setAutoCheckEnabled(boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_AUTO_CHECK, enabled).apply();
    }

    /**
     * Check if enough time has passed since the last update check.
     */
    public boolean shouldCheckForUpdates() {
        if (!isAutoCheckEnabled()) {
            return false;
        }
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(PREF_LAST_CHECK, 0);
        return System.currentTimeMillis() - lastCheck > CHECK_INTERVAL_MS;
    }

    /**
     * Get the current app version.
     */
    public String getCurrentVersion() {
        try {
            PackageInfo pInfo = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0.0.0";
        }
    }

    /**
     * Get the current version code.
     */
    public int getCurrentVersionCode() {
        try {
            PackageInfo pInfo = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) pInfo.getLongVersionCode();
            } else {
                return pInfo.versionCode;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    // Helper methods

    private UpdateInfo parseRelease(JsonObject release) {
        UpdateInfo info = new UpdateInfo();
        
        // Get version from tag name
        String tagName = release.has("tag_name") ? release.get("tag_name").getAsString() : "";
        info.setVersion(tagName.startsWith("v") ? tagName.substring(1) : tagName);
        
        // Get release notes from body
        info.setReleaseNotes(release.has("body") ? release.get("body").getAsString() : "");
        
        // Get published date
        info.setPublishedAt(release.has("published_at") ? release.get("published_at").getAsString() : "");
        
        // Find APK asset
        if (release.has("assets") && release.get("assets").isJsonArray()) {
            JsonArray assets = release.getAsJsonArray("assets");
            
            for (int i = 0; i < assets.size(); i++) {
                JsonObject asset = assets.get(i).getAsJsonObject();
                String name = asset.has("name") ? asset.get("name").getAsString() : "";
                
                if (name.endsWith(".apk")) {
                    info.setDownloadUrl(asset.has("browser_download_url") ? 
                        asset.get("browser_download_url").getAsString() : "");
                    info.setFileSize(asset.has("size") ? asset.get("size").getAsLong() : 0);
                    break;
                }
            }
        }
        
        return info;
    }

    private boolean isNewerVersion(String newVersion) {
        String currentVersion = getCurrentVersion();
        return compareVersions(newVersion, currentVersion) > 0;
    }

    private int compareVersions(String v1, String v2) {
        // Remove any 'v' prefix
        v1 = v1.startsWith("v") ? v1.substring(1) : v1;
        v2 = v2.startsWith("v") ? v2.substring(1) : v2;
        
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        int length = Math.max(parts1.length, parts2.length);
        
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            
            if (num1 > num2) return 1;
            if (num1 < num2) return -1;
        }
        
        return 0;
    }

    private int parseVersionPart(String part) {
        try {
            // Remove any non-numeric suffix (e.g., "1-beta" -> "1")
            String numericPart = part.replaceAll("[^0-9].*", "");
            return Integer.parseInt(numericPart);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void registerDownloadReceiver() {
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    handleDownloadComplete();
                }
            }
        };
        
        context.registerReceiver(downloadReceiver, 
            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void handleDownloadComplete() {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        
        Cursor cursor = downloadManager.query(query);
        if (cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = cursor.getInt(columnIndex);
            
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                String localUri = cursor.getString(uriIndex);
                File apkFile = new File(Uri.parse(localUri).getPath());
                
                if (callback != null) {
                    mainHandler.post(() -> callback.onDownloadComplete(apkFile));
                }
            } else {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Download failed"));
                }
            }
        }
        cursor.close();
    }

    private void monitorDownloadProgress() {
        executor.execute(() -> {
            while (downloadId >= 0) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                
                Cursor cursor = downloadManager.query(query);
                if (cursor.moveToFirst()) {
                    int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(statusIndex);
                    
                    if (status == DownloadManager.STATUS_RUNNING) {
                        int downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                        
                        long downloaded = cursor.getLong(downloadedIndex);
                        long total = cursor.getLong(totalIndex);
                        
                        if (total > 0) {
                            int progress = (int) ((downloaded * 100) / total);
                            if (callback != null) {
                                mainHandler.post(() -> callback.onDownloadProgress(progress));
                            }
                        }
                    } else if (status == DownloadManager.STATUS_SUCCESSFUL || 
                               status == DownloadManager.STATUS_FAILED) {
                        cursor.close();
                        break;
                    }
                }
                cursor.close();
                
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    private void cleanupOldApks() {
        File downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir != null && downloadDir.exists()) {
            File[] files = downloadDir.listFiles((dir, name) -> name.startsWith("termux-ai-") && name.endsWith(".apk"));
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }

    public void cleanup() {
        if (downloadReceiver != null) {
            try {
                context.unregisterReceiver(downloadReceiver);
            } catch (Exception ignored) {}
        }
        executor.shutdown();
    }
}
