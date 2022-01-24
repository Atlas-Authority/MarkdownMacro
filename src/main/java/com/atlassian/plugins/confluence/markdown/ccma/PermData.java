package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.sal.api.user.UserKey;

import java.util.Collections;
import java.util.Set;

class PermData {
    private final Set<UserKey> users;
    private final Set<String> groups;

    static final PermData empty = new PermData(Collections.emptySet(), Collections.emptySet());

    PermData(Set<UserKey> users, Set<String> groups) {
        this.users = users;
        this.groups = groups;
    }

    Set<UserKey> getUsers() {
        return users;
    }

    Set<String> getGroups() {
        return groups;
    }

    boolean isEmpty() {
        return users.isEmpty() && groups.isEmpty();
    }
}
