package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.api.model.content.Content;

class PageData {
    private final String serverId;
    private final String cloudId;
    private final long serverSpaceId;
    private final Content page;
    private String cloudUserKey;

    PageData(String serverId, String cloudId, long serverSpaceId, Content page) {
        this.serverId = serverId;
        this.cloudId = cloudId;
        this.serverSpaceId = serverSpaceId;
        this.page = page;
    }

    String getServerId() {
        return serverId;
    }

    String getCloudId() {
        return cloudId;
    }

    long getServerSpaceId() {
        return serverSpaceId;
    }

    Content getPage() {
        return page;
    }

    String getCloudUserKey() {
        return cloudUserKey;
    }

    void setCloudUserKey(String cloudUserKey) {
        this.cloudUserKey = cloudUserKey;
    }
}
