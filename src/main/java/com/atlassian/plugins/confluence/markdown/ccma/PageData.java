package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.sal.api.user.UserKey;

import java.util.Set;

class PageData {
    private final String cloudId;
    private final long serverSpaceId;
    private final String body;
    private final Set<UserKey> restrictedUserKeys;


    public PageData(String cloudId, long serverSpaceId, String body, Set<UserKey> restrictedUserKeys) {
        this.cloudId = cloudId;
        this.serverSpaceId = serverSpaceId;
        this.body = body;
        this.restrictedUserKeys = restrictedUserKeys;
    }

    public String getCloudId() {
        return cloudId;
    }

    public long getServerSpaceId() {
        return serverSpaceId;
    }

    public String getBody() {
        return body;
    }

    public Set<UserKey> getRestrictedUserKeys() {
        return restrictedUserKeys;
    }
}
