package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.api.model.content.Content;

class PageData {
    private final String serverId;
    private final String cloudId;
    private final long serverSpaceId;
    private final Content page;
    private String cloudUserKey;

    public PageData(String serverId, String cloudId, long serverSpaceId, Content page) {
        this.serverId = serverId;
        this.cloudId = cloudId;
        this.serverSpaceId = serverSpaceId;
        this.page = page;
    }

    public String getServerId() {
        return serverId;
    }

    public String getCloudId() {
        return cloudId;
    }

    public long getServerSpaceId() {
        return serverSpaceId;
    }

    public Content getPage() {
        return page;
    }

    public String getCloudUserKey() {
        return cloudUserKey;
    }

    public void setCloudUserKey(String cloudUserKey) {
        this.cloudUserKey = cloudUserKey;
    }
}
