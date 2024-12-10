package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.migration.app.PaginatedMapping;
import com.atlassian.migration.app.gateway.AppCloudMigrationGateway;
import com.atlassian.plugin.spring.scanner.annotation.imports.ConfluenceImport;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.user.Group;
import org.apache.commons.collections4.SetUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.stream.StreamSupport;

@Named
class UserService {
    private static final String USER_MAPPING_PREFIX = "confluence.userkey/";
    private static final int BATCH_SIZE = 5;

    private final UserAccessor userAccessor;
    private final SpacePermissionService spacePermissionService;
    private final PageRestrictionService pageRestrictionService;

    private final LinkedHashMap<String, Map<String, String>> userMapCache = new LinkedHashMap<String, Map<String, String>>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Map<String, String>> eldest) {
                return this.size() > 2;//we only cache users for up to 2 transfers
            }
    };

    @Inject
    UserService(
            @ConfluenceImport UserAccessor userAccessor,
            SpacePermissionService spacePermissionService,
            PageRestrictionService pageRestrictionService
    ) {
        this.userAccessor = userAccessor;
        this.spacePermissionService = spacePermissionService;
        this.pageRestrictionService = pageRestrictionService;
    }

    /**
     * Populate a use who has edit permission for each page.
     */
    void enrichCloudUser(AppCloudMigrationGateway gateway, String transferId, List<PageData> pageDataList) {
        if (pageDataList.isEmpty()) {
            return;
        }

        final Map<String, String> userMap = getUserMap(gateway, transferId);
        final Map<Long, PermData> spacePermissions = spacePermissionService.getPermissions(pageDataList);
        final Map<String, PermData> pageRestrictions = pageRestrictionService.getPermissions(pageDataList);

        final Helper helper = new Helper();
        for (PageData pageData : pageDataList) {
            final String cloudUserKey = helper.pickUserWhoHasEditPerm(pageData, spacePermissions, pageRestrictions, userMap);
            pageData.setUserWithEditCloudId(cloudUserKey);
        }
    }

    /**
     * User mapping from server id to cloud id.
     */
    private Map<String, String> getUserMap(AppCloudMigrationGateway gateway, String transferId) {
        return userMapCache.computeIfAbsent(transferId, (String sid) -> {
            final PaginatedMapping paginatedMapping = gateway.getPaginatedMapping(sid, "identity:user", BATCH_SIZE);
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
        });
    }

    /**
     * User picker implementation with cache.
     */
    private class Helper {
        final Map<String, Optional<UserKey>> userByGroupCache = new HashMap<>();
        final Map<UserKey, Set<String>> groupsByUserCache = new HashMap<>();

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
        private String pickUserWhoHasEditPerm(
                PageData pageData,
                Map<Long, PermData> spacePermissions,
                Map<String, PermData> pageRestrictions,
                Map<String, String> userMap
        ) {
            final long spaceId = Long.parseLong(pageData.getSpaceServerId());
            final PermData pagePermission = pageRestrictions.getOrDefault(pageData.getPageServerId(), PermData.empty);
            final PermData spacePermission = spacePermissions.getOrDefault(spaceId, PermData.empty);

            Optional<UserKey> userKeyOpt;
            if (pagePermission.isEmpty()) {
                userKeyOpt = pickUserFromSpace(spacePermission);
            } else {
                final Set<UserKey> pageUsers = pagePermission.getUsers();
                final Set<String> pageGroups = pagePermission.getGroups();

                final Set<UserKey> spaceUsers = spacePermission.getUsers();
                final Set<String> spaceGroups = spacePermission.getGroups();

                userKeyOpt = pickCommonUser(pageUsers, spaceUsers);

                if (!userKeyOpt.isPresent()) {
                    userKeyOpt = pickUserIfInGroup(pageUsers, spaceGroups);
                }

                if (!userKeyOpt.isPresent()) {
                    userKeyOpt = pickUserIfInGroup(spaceUsers, pageGroups);
                }

                if (!userKeyOpt.isPresent()) {
                    userKeyOpt = pickUserInCommonGroup(pageGroups, spaceGroups);
                }
            }

            return userKeyOpt
                    .map(UserKey::getStringValue)
                    .map(USER_MAPPING_PREFIX::concat)
                    .map(userMap::get)
                    .orElse(null);
        }

        private Optional<UserKey> pickUserFromSpace(PermData spacePermission) {
            final Optional<UserKey> userKey = spacePermission.getUsers().stream().findAny();
            if (userKey.isPresent()) {
                return userKey;
            } else {
                return pickUserInGroups(spacePermission.getGroups());
            }
        }

        private Optional<UserKey> pickCommonUser(Set<UserKey> pageUsers, Set<UserKey> spaceUsers) {
            return SetUtils
                    .intersection(pageUsers, spaceUsers)
                    .stream()
                    .findAny();
        }

        private Optional<UserKey> pickUserIfInGroup(Set<UserKey> userKeys, Set<String> groups) {
            return userKeys.stream().filter(userKey -> {
                final Set<String> userGroups = getUserGroups(userKey);
                return !SetUtils.intersection(userGroups, groups).isEmpty();
            }).findFirst();
        }

        private Optional<UserKey> pickUserInCommonGroup(Set<String> pageGroups, Set<String> spaceGroups) {
            final Set<String> commonGroups = SetUtils.intersection(pageGroups, spaceGroups);
            return pickUserInGroups(commonGroups);
        }

        private Optional<UserKey> pickUserInGroups(Set<String> groups) {
            return groups
                    .stream()
                    .map(this::pickUserInGroup)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
        }

        private Optional<UserKey> pickUserInGroup(String groupName) {
            return userByGroupCache.computeIfAbsent(groupName, __ -> {
                final Group group = userAccessor.getGroup(groupName);
                final Spliterator<ConfluenceUser> spliterator = Spliterators.spliteratorUnknownSize(
                        userAccessor.getMembers(group).iterator(),
                        Spliterator.ORDERED
                );
                return StreamSupport.stream(spliterator, false)
                        .map(ConfluenceUser::getKey)
                        .findFirst();
            });
        }

        private Set<String> getUserGroups(UserKey userKey) {
            return groupsByUserCache.computeIfAbsent(userKey, __ -> {
                final ConfluenceUser user = userAccessor.getUserByKey(userKey);
                if (user == null) {
                    return Collections.emptySet();
                }

                return new HashSet<>(userAccessor.getGroupNames(user));
            });
        }
    }
}
