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
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.OutputStream;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.StreamSupport;

import static com.atlassian.migration.app.AccessScope.*;

@Named
@ExportAsService
public class MyPluginComponentImpl implements DiscoverableListener {

    private static final Logger log = LoggerFactory.getLogger(MyPluginComponentImpl.class);
    private static final int BATCH_SIZE = 5000;

    private final ContentService contentService;
    private final UserAccessor userAccessor;

    private final PageFilter pageFilter;
    private final String serverAppVersion;

    private final UserService userService;

    @Inject
    public MyPluginComponentImpl(
            @Value("${build.version}") String serverAppVersion,
            @ConfluenceImport ContentService contentService,
            @ConfluenceImport UserAccessor userAccessor,
            PageFilter pageFilter,
            UserService userService
    ) {
        this.contentService = contentService;
        this.userAccessor = userAccessor;
        this.pageFilter = pageFilter;
        this.serverAppVersion = serverAppVersion;
        this.userService = userService;
    }

    @Override
    public void onStartAppMigration(AppCloudMigrationGateway gateway, String transferId, MigrationDetailsV1 migrationDetails) {
        log.info("Markdown macro migration context summary: \n\t{}\n\t{}\n\t{}\n\t{}\n\t{}\n\t{}",
                "Name: " + migrationDetails.getName(),
                "Cloud Url: " + migrationDetails.getCloudUrl(),
                "Migration ID: " + migrationDetails.getMigrationId(),
                "Migration Scope ID: " + migrationDetails.getMigrationScopeId(),
                "Client Key: " + migrationDetails.getConfluenceClientKey(),
                "Created At: " + migrationDetails.getCreatedAt()
        );

        try {
            byte[] payload;
            try {
                payload = preparePayload(gateway, transferId);
            } catch (Exception exception) {
                log.error("Error while preparing payload for app migration {}, transfer ID: {}, migrationID: {}",
                        migrationDetails.getName(),
                        transferId,
                        migrationDetails.getMigrationId(),
                        exception
                );
                payload = errorPayload(exception);
            }
            uploadAppData(gateway, transferId, payload);
            log.info("Finished migration {}, transfer ID: {}, migrationID: {}",
                    migrationDetails.getName(),
                    transferId,
                    migrationDetails.getMigrationId()
            );
        } catch (Exception exception) {
            log.error("Error while running app migration {}, transfer ID: {}, migrationID: {}",
                    migrationDetails.getName(),
                    transferId,
                    migrationDetails.getMigrationId(),
                    exception
            );
        }
    }

    private byte[] preparePayload(AppCloudMigrationGateway gateway, String transferId) throws IOException {
        log.info("Start preparing migration payload");
        final Optional<ConfluenceUser> adminUser = pickAdminUser();
        if (adminUser.isPresent()) {
            AuthenticatedUserThreadLocal.set(adminUser.get());
        } else {
            throw new RuntimeException("Please make sure confluence-administrators has at least 1 user");
        }

        final List<PageData> pageDataList = getPageDataList(gateway, transferId);
        userService.enrichCloudUser(gateway, transferId, pageDataList);

        final ObjectMapper objectMapper = new ObjectMapper();

        final ObjectNode topLevelNode = objectMapper.createObjectNode();
        topLevelNode.put("serverAppVersion", serverAppVersion);

        final ArrayNode pagesNode = topLevelNode.putArray("pages");
        final ArrayNode cloudPageIdsNode = topLevelNode.putArray("cloudPageId");
        for (PageData pageData: pageDataList) {
            cloudPageIdsNode.add(pageData.getCloudId());
            final ObjectNode page = pagesNode.addObject();
            page.put("pageId", pageData.getCloudId());
            page.put("accountId", pageData.getCloudUserKey());
        }

        log.info("Finished preparing migration payload. Total pages: {}", cloudPageIdsNode.size());

        return objectMapper.writeValueAsBytes(topLevelNode);
    }

    private byte[] errorPayload(Exception e) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();
        final ObjectNode topLevelNode = objectMapper.createObjectNode();
        topLevelNode.put("error", "Error while preparing migration payload in server app: " + e.getMessage());
        return objectMapper.writeValueAsBytes(topLevelNode);
    }

    private void uploadAppData(AppCloudMigrationGateway gateway, String transferId, byte[] data) throws IOException {
        Failsafe.with(buildRetryPolicy()).run(() -> {
            final OutputStream outputStream = gateway.createAppData(transferId);
            outputStream.write(data);
            outputStream.close();
        });
    }

    private RetryPolicy<Object> buildRetryPolicy() {
        return RetryPolicy.builder()
                .withMaxRetries(5)
                .withBackoff(2L, 30L, ChronoUnit.SECONDS)
                .onFailedAttempt(failure -> {
                    log.warn("Failed to upload app data. Attempt {}", failure.getAttemptCount(), failure.getLastException());
                })
                .onRetriesExceeded(failure -> {
                    log.error("Retries exceeded", failure.getException());
                })
                .build();
    }

    /**
     * Authenticate a random admin user to enable Confluence service APIs.
     */
    private Optional<ConfluenceUser> pickAdminUser() {
        return Optional
                .ofNullable(userAccessor.getGroup("confluence-administrators"))
                .flatMap(adminGroup -> StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(
                                userAccessor.getMembers(adminGroup).iterator(),
                                Spliterator.ORDERED
                        ),
                        false
                ).findFirst());
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
                .find(ExpansionsParser.parse("history,body.storage,space"))
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
