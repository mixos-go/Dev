package com.termux.app.browser;

import java.util.UUID;

public class BrowserTab {
    private String id;
    private String url;
    private String title;
    private boolean isActive;
    private long createdAt;

    public BrowserTab(String url, String title) {
        this.id = UUID.randomUUID().toString();
        this.url = url;
        this.title = title;
        this.isActive = true;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getDisplayTitle() {
        if (title != null && !title.isEmpty() && !title.equals(url)) {
            return title.length() > 30 ? title.substring(0, 30) + "..." : title;
        }
        if (url != null) {
            // Extract domain from URL
            try {
                String domain = url.replaceFirst("https?://", "").split("/")[0];
                return domain.length() > 30 ? domain.substring(0, 30) + "..." : domain;
            } catch (Exception e) {
                return url.length() > 30 ? url.substring(0, 30) + "..." : url;
            }
        }
        return "New Tab";
    }
}
