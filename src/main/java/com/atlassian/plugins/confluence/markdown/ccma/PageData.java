package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.api.model.content.Content;

class PageData {
    private final String pageCloudId;
    private final String pageServerId;

    private final String spaceCloudId;
    private final String spaceServerId;
    private final String spaceKey;

    private final Content pageContent;

    private String userWithEditCloudId;

    public PageData(String pageCloudId, String pageServerId, String spaceCloudId, String spaceServerId, String spaceKey, Content pageContent) {
        this.pageCloudId = pageCloudId;
        this.pageServerId = pageServerId;
        this.spaceCloudId = spaceCloudId;
        this.spaceServerId = spaceServerId;
        this.spaceKey = spaceKey;
        this.pageContent = pageContent;
    }

    public String getPageCloudId() {
        return pageCloudId;
    }

    public String getPageServerId() {
        return pageServerId;
    }

    public String getSpaceCloudId() {
        return spaceCloudId;
    }

    public String getSpaceServerId() {
        return spaceServerId;
    }

    public String getSpaceKey() {
        return spaceKey;
    }

    public Content getPageContent() {
        return pageContent;
    }

    public String getUserWithEditCloudId() {
        return userWithEditCloudId;
    }

    public void setUserWithEditCloudId(String userWithEditCloudId) {
        this.userWithEditCloudId = userWithEditCloudId;
    }
}
