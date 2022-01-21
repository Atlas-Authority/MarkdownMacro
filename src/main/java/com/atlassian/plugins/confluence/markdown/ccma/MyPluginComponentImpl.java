package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.api.model.content.*;
import com.atlassian.confluence.api.model.content.id.ContentId;
import com.atlassian.confluence.api.service.content.ContentService;
import com.atlassian.confluence.rest.api.model.ExpansionsParser;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.user.UserAccessor;
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
import java.util.stream.StreamSupport;

import static com.atlassian.migration.app.AccessScope.*;

@Named
@ExportAsService
public class MyPluginComponentImpl implements DiscoverableListener {

    private static final Logger log = LoggerFactory.getLogger(MyPluginComponentImpl.class);
    private static final int BATCH_SIZE = 5;

    private final ContentService contentService;
    private final UserAccessor userAccessor;

    private final PageFilter pageFilter;
    private final String serverAppVersion;

    private final SpacePermissionService spacePermissionService;
    private final PageRestrictionService pageRestrictionService;
    private final UserService userService;

    @Inject
    public MyPluginComponentImpl(
            @Value("${build.version}") String serverAppVersion,
            @ConfluenceImport ContentService contentService,
            @ConfluenceImport UserAccessor userAccessor,
            PageFilter pageFilter,
            SpacePermissionService spacePermissionService,
            PageRestrictionService pageRestrictionService,
            UserService userService
    ) {
        this.contentService = contentService;
        this.userAccessor = userAccessor;
        this.pageFilter = pageFilter;
        this.serverAppVersion = serverAppVersion;
        this.spacePermissionService = spacePermissionService;
        this.pageRestrictionService = pageRestrictionService;
        this.userService = userService;
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

            final List<PageData> pageDataList = getPageDataList(gateway, transferId);

            final Map<String, String> userMap = userService.getUserMap(gateway, transferId);

            final Map<Long, Set<UserKey>> spacePermissions = spacePermissionService.getPermissions(pageDataList);

            final Map<String, Set<UserKey>> pageRestrictions = pageRestrictionService.getPermissions(pageDataList);

            final ObjectMapper overallMapper = new ObjectMapper();
            final ObjectNode topLevelNode = overallMapper.createObjectNode();

            topLevelNode.put("serverAppVersion", serverAppVersion);

            final ArrayNode pagesNode = topLevelNode.putArray("pages");
            final ArrayNode cloudPageIdsNode = topLevelNode.putArray("cloudPageId");
            for (PageData pageData: pageDataList) {
                final ObjectNode page = pagesNode.addObject();
                final String userCloudKey = userService.pickUserWhoHasEditPerm(
                        pageData,
                        spacePermissions,
                        pageRestrictions,
                        userMap
                );

                cloudPageIdsNode.add(pageData.getCloudId());

                page.put("pageId", pageData.getCloudId());
                page.put("accountId", userCloudKey);
            }

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
        return contentService
                .find(ExpansionsParser.parse("history,body.storage,space,restrictions.update.restrictions.user"))
                .withType(ContentType.PAGE)
                .withId(ContentId.of(Long.parseLong(serverPageId)))
                .fetch()
                .filter(this::isLatestVersion)
                .filter(this::hasMarkdownMarcoFromUrl)
                .map(page -> new PageData(serverPageId, cloudPageId, page.getSpace().getId(), page));
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
