package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.migration.app.PaginatedMapping;
import com.atlassian.migration.app.gateway.AppCloudMigrationGateway;
import com.atlassian.plugin.spring.scanner.annotation.imports.ConfluenceImport;
import com.atlassian.sal.api.user.UserKey;
import org.apache.commons.collections4.SetUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

@Named
class UserService {
    private static final String USER_MAPPING_PREFIX = "confluence.userkey/";
    private static final int BATCH_SIZE = 5;

    @Inject
    UserService(@ConfluenceImport UserAccessor userAccessor) {
    }

    /**
     * Pick a user who has edit permission on page.
     *
     * If a page is restricted then pick a user who has edit perm in
     * both page and space.
     *
     * If a page is not restricted then pick a user from space restriction settings.
     *
     * Return null if:
     * <li>Space is public and allows anonymous access (global space permission) OR</li>
     * <li>Space is not editable entirely, such config is uncommon</li>
     */
    String pickUserWhoHasEditPerm(
            PageData pageData,
            Map<Long, Set<UserKey>> spacePermissions,
            Map<String, Set<UserKey>> pageRestrictions,
            Map<String, String> userMap
    ) {
        final long spaceId = pageData.getServerSpaceId();
        final Set<UserKey> pageRestrictedUsers = pageRestrictions.getOrDefault(pageData.getServerId(), Collections.emptySet());
        final Set<UserKey> spaceRestrictedUsers = spacePermissions.getOrDefault(spaceId, Collections.emptySet());

        final Set<UserKey> satisfiedUsers = pageRestrictedUsers.isEmpty()
                ? spaceRestrictedUsers
                : SetUtils.intersection(spaceRestrictedUsers, pageRestrictedUsers);

        return satisfiedUsers
                .stream()
                .map(UserKey::getStringValue)
                .map(USER_MAPPING_PREFIX::concat)
                .map(userMap::get)
                .filter(Objects::nonNull)
                .findAny()
                .orElse(null);
    }

    /**
     * User mapping from server id to cloud id.
     */
    Map<String, String> getUserMap(AppCloudMigrationGateway gateway, String transferId) {
        final PaginatedMapping paginatedMapping = gateway.getPaginatedMapping(transferId, "identity:user", BATCH_SIZE);
        final Map<String, String> results = new HashMap<>();
        while (paginatedMapping.next()) {
            final Map<String, String> mappings = paginatedMapping.getMapping();
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                String serverUserKey = entry.getKey();
                String cloudUserKey = entry.getValue();
                if (serverUserKey.startsWith(USER_MAPPING_PREFIX)) {
                    results.put(serverUserKey, cloudUserKey);
                }
            }
        }
        return Collections.unmodifiableMap(results);
    }
}
