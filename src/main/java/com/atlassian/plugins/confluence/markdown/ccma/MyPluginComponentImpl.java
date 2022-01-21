package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.api.model.content.*;
import com.atlassian.confluence.api.model.content.id.ContentId;
import com.atlassian.confluence.api.model.pagination.PageResponse;
import com.atlassian.confluence.api.model.people.Subject;
import com.atlassian.confluence.api.model.people.SubjectType;
import com.atlassian.confluence.api.model.people.User;
import com.atlassian.confluence.api.model.permissions.ContentRestriction;
import com.atlassian.confluence.api.model.permissions.OperationKey;
import com.atlassian.confluence.api.service.content.ContentService;
import com.atlassian.confluence.rest.api.model.ExpansionsParser;
import com.atlassian.confluence.security.SpacePermission;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.migration.app.AccessScope;
import com.atlassian.migration.app.PaginatedMapping;
import com.atlassian.migration.app.gateway.AppCloudMigrationGateway;
import com.atlassian.migration.app.gateway.MigrationDetailsV1;
import com.atlassian.migration.app.listener.DiscoverableListener;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ConfluenceImport;
import com.atlassian.sal.api.user.UserKey;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.atlassian.migration.app.AccessScope.*;

@Named
@ExportAsService
public class MyPluginComponentImpl implements DiscoverableListener {

    private static final Logger log = LoggerFactory.getLogger(MyPluginComponentImpl.class);
    private static final String USER_MAPPING_PREFIX = "confluence.userkey/";
    private static final int BATCH_SIZE = 5;

    @ConfluenceImport private final SpaceManager spaceManager;
    @ConfluenceImport private final ContentService contentService;
    @ConfluenceImport private final UserAccessor userAccessor;
    private final PageFilter pageFilter;
    private final String serverAppVersion;

    @Inject
    public MyPluginComponentImpl(
            @Value("${build.version}") String serverAppVersion,
            @ConfluenceImport SpaceManager spaceManager,
            @ConfluenceImport ContentService contentService,
            @ConfluenceImport UserAccessor userAccessor,
            @ConfluenceImport XhtmlContent xhtmlContent
    ) {
        // It is not safe to save a direct reference to the gateway as that can change over time
        this.spaceManager = spaceManager;
        this.contentService = contentService;
        this.userAccessor = userAccessor;
        this.pageFilter = new PageFilter(xhtmlContent);
        this.serverAppVersion = serverAppVersion;
    }

    @Override
    public void onStartAppMigration(AppCloudMigrationGateway gateway, String transferId, MigrationDetailsV1 migrationDetails) {
        try(final OutputStream stream = gateway.createAppData(transferId)) {
            final boolean isSetupAdminUserSuccess = setupAdminUser();
            if (!isSetupAdminUserSuccess) {
                throw new RuntimeException("Please make sure confluence-administrators has at least 1 user");
            }

            final ObjectMapper objectMapper = new ObjectMapper();
            log.info("Migration context summary: " + objectMapper.writeValueAsString(migrationDetails));

            final Map<String, String> userMap = getUserMap(gateway, transferId);

            final List<PageData> pageDataList = getPageDataList(gateway, transferId);

            final Set<Long> spaceIds = pageDataList
                    .stream()
                    .map(PageData::getServerSpaceId)
                    .collect(Collectors.toSet());

            final Map<Long, Set<UserKey>> spaceUsersById = getSpacePermissions(spaceIds);

            final ObjectMapper overallMapper = new ObjectMapper();
            final ObjectNode topLevelNode = overallMapper.createObjectNode();

            final ArrayNode pagesNode = topLevelNode.putArray("pages");
            for (PageData pageData: pageDataList) {
                final ObjectNode page = pagesNode.addObject();
                final String userCloudKey = pickUserWhoHasEditPerm(pageData, spaceUsersById, userMap);
                page.put("pageId", pageData.getCloudId());
                page.put("accountId", userCloudKey);
            }

            topLevelNode.put("serverAppVersion", serverAppVersion);

            stream.write(overallMapper.writeValueAsString(topLevelNode).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error while running app migration", e);
        }
    }

    /**
     * Authenticate a random admin user to enable Confluence service APIs.
     */
    private boolean setupAdminUser() {
        final Optional<ConfluenceUser> adminUser = Optional
                .ofNullable(userAccessor.getGroup("confluence-administrators"))
                .flatMap(adminGroup -> StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(
                                userAccessor.getMembers(adminGroup).iterator(),
                                Spliterator.ORDERED
                        ),
                        false
                ).findFirst());
        adminUser.ifPresent(AuthenticatedUserThreadLocal::set);

        return adminUser.isPresent();
    }

    /**
     * Collect data of all pages which need to migrate.
     */
    private List<PageData> getPageDataList(AppCloudMigrationGateway gateway, String transferId) {
        PaginatedMapping paginatedMapping = gateway.getPaginatedMapping(transferId, "confluence:page", BATCH_SIZE);
        final List<PageData> pageDataList = new ArrayList<>();
        while (paginatedMapping.next()) {
            Map<String, String> mappings = paginatedMapping.getMapping();
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                String serverPageId = entry.getKey();
                String cloudPageId = entry.getValue();
                getLatestPageData(serverPageId, cloudPageId).ifPresent(pageDataList::add);
            }
        }
        return Collections.unmodifiableList(pageDataList);
    }

    /**
     * Check if a server page is latest and needs migrate.
     */
    private Optional<PageData> getLatestPageData(String serverPageId, String cloudPageId) {
        final Optional<Content> pageOpt = contentService
                .find(ExpansionsParser.parse("history,body.storage,space,restrictions.update.restrictions.user"))
                .withType(ContentType.PAGE)
                .withId(ContentId.of(Long.parseLong(serverPageId)))
                .fetch();

        return pageOpt
                .filter(this::isLatestVersion)
                .filter(this::hasMarkdownMarcoFromUrl)
                .map(page -> {
                    final List<Subject> restrictedUsers = Optional.ofNullable(page.getRestrictions())
                            .map(restrictions -> restrictions.get(OperationKey.UPDATE))
                            .map(ContentRestriction::getRestrictions)
                            .map(restrictions -> restrictions.get(SubjectType.USER))
                            .map(PageResponse::getResults)
                            .orElse(Collections.emptyList());

                    final Set<UserKey> restrictedUserKeys = restrictedUsers
                            .stream()
                            .map(subject -> (User) subject)
                            .map(User::optionalUserKey)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toSet());

                    return new PageData(
                            cloudPageId,
                            page.getSpace().getId(),
                            page.getBody().get(ContentRepresentation.STORAGE).getValue(),
                            restrictedUserKeys
                    );
                });
    }

    private boolean isLatestVersion(Content page) {
        return Optional
                .ofNullable(page.getHistory())
                .map(History::isLatest)
                .orElse(false);
    }

    /**
     * Check if the page contains Markdown macro from URL.
     * Only markdown macros from URL need to migrate.
     */
    private boolean hasMarkdownMarcoFromUrl(Content page) {
        return Optional
                .ofNullable(page.getBody())
                .map(contentMap -> contentMap.get(ContentRepresentation.STORAGE))
                .map(ContentBody::getValue)
                .filter(pageFilter::hasMarkdownMacroFromUrl)
                .isPresent();
    }

    /**
     * Pick a user who has edit permission on page.
     *
     * Return null if the permission is not controlled at user level:
     * <li>Space is public and allows anonymous access (global space permission) OR</li>
     * <li>Space permissions are controlled at group level. In this case, since cloud app has
     * READ and WRITE scope so it totally has access to space content.</li>
     */
    private String pickUserWhoHasEditPerm(
            PageData pageData,
            Map<Long, Set<UserKey>> spaceUsersById,
            Map<String, String> userMap
    ) {
        Optional<UserKey> userKeyOpt;
        final Set<UserKey> pageRestrictedUsers = pageData.getRestrictedUserKeys();
        if (!pageRestrictedUsers.isEmpty()) {
            // if this page has update restriction config then pick user from it
            userKeyOpt = pageRestrictedUsers.stream().findAny();
        } else {
            // otherwise pick a user at space level
            final long spaceId = pageData.getServerSpaceId();
            userKeyOpt = spaceUsersById
                    .getOrDefault(spaceId, Collections.emptySet())
                    .stream()
                    .findAny();
        }

        return userKeyOpt
                .map(UserKey::getStringValue)
                .map(USER_MAPPING_PREFIX::concat)
                .map(userMap::get)
                .orElse(null);
    }

    /**
     * Mapping from space server id to a set of users who has edit permission on that space.
     */
    private Map<Long, Set<UserKey>> getSpacePermissions(Set<Long> spaceIds) {
        return spaceIds.stream().collect(Collectors.toMap(Function.identity(), this::getEditSpaceUsers));
    }

    /**
     * Get server users who has edit permission on space
     */
    private Set<UserKey> getEditSpaceUsers(long spaceId) {
        final Space space = spaceManager.getSpace(spaceId);
        if (space == null) {
            return Collections.emptySet();
        }
        final Set<UserKey> userKeys = space.getPermissions().stream()
                .filter(SpacePermission::isUserPermission)
                .filter(perm -> SpacePermission.CREATEEDIT_PAGE_PERMISSION.equalsIgnoreCase(perm.getType()))
                .map(SpacePermission::getUserSubject)
                .filter(Objects::nonNull)
                .map(ConfluenceUser::getKey)
                .collect(Collectors.toSet());
        return Collections.unmodifiableSet(userKeys);
    }

    /**
     * User mapping from server id to cloud id.
     */
    private Map<String, String> getUserMap(AppCloudMigrationGateway gateway, String transferId) {
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

    @Override
    public String getCloudAppKey() {
        return "com.atlassian.plugins.confluence.markdown.confluence-markdown-macro";
    }

    @Override
    public String getServerAppKey() {
        return "com.atlassian.plugins.confluence.markdown.confluence-markdown-macro";
    }

    @Override
    public Set<AccessScope> getDataAccessScopes() {
        final Set<AccessScope> accessScopes = new HashSet<>();
        accessScopes.add(APP_DATA_OTHER);
        accessScopes.add(MIGRATION_TRACING_PRODUCT);
        accessScopes.add(MIGRATION_TRACING_IDENTITY);
        return Collections.unmodifiableSet(accessScopes);
    }
}
