package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.api.model.content.*;
import com.atlassian.confluence.api.model.pagination.PageRequest;
import com.atlassian.confluence.api.model.pagination.PageResponse;
import com.atlassian.confluence.api.model.pagination.SimplePageRequest;
import com.atlassian.confluence.api.service.search.CQLSearchService;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.migration.app.PaginatedMapping;
import com.atlassian.migration.app.gateway.AppCloudMigrationGateway;
import com.atlassian.migration.app.gateway.MigrationDetailsV1;
import com.atlassian.user.Group;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import org.apache.commons.collections4.ListUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

class Migrator {
    private static final Logger log = LoggerFactory.getLogger(Migrator.class);
    private static final int CQL_BATCH_SIZE = 1000;
    private static final String STARTING_PAYLOAD = "STARTING_PAYLOAD";
    private static final String DATA_PAYLOAD = "DATA_PAYLOAD";
    private static final String ERROR_PAYLOAD = "ERROR_PAYLOAD";
    private static final String ENDING_PAYLOAD = "ENDING_PAYLOAD";

    private final SpaceManager spaceManager;
    private final CQLSearchService cqlSearchService;
    private final UserAccessor userAccessor;
    private final String serverAppVersion;
    private final UserService userService;

    private final AppCloudMigrationGateway gateway;
    private final String transferId;
    private final MigrationDetailsV1 migrationDetails;

    Migrator(
            SpaceManager spaceManager,
            CQLSearchService cqlSearchService,
            UserAccessor userAccessor,
            String serverAppVersion,
            UserService userService,
            AppCloudMigrationGateway gateway,
            String transferId,
            MigrationDetailsV1 migrationDetails
    ) {
        this.spaceManager = spaceManager;
        this.cqlSearchService = cqlSearchService;
        this.userAccessor = userAccessor;
        this.serverAppVersion = serverAppVersion;
        this.userService = userService;
        this.gateway = gateway;
        this.transferId = transferId;
        this.migrationDetails = migrationDetails;
    }

    void migrate() {
        logMigrationDetails("Markdown macro migration context summary");
        try {
            setAdminUser();
            uploadStartingPayload();
            int totalChunkCount = uploadChunks();
            uploadEndingPayload(totalChunkCount);
        } catch (Exception e) {
            logMigrationDetails("Error while running app migration");
        }
    }

    private void uploadStartingPayload() throws IOException {
        log.info("Start uploading starting payload");
        final ObjectMapper objectMapper = new ObjectMapper();
        final ObjectNode topLevelNode = objectMapper.createObjectNode();
        topLevelNode.put("serverAppVersion", serverAppVersion);
        upload(STARTING_PAYLOAD, objectMapper.writeValueAsBytes(topLevelNode));
    }

    private int uploadChunks() throws IOException {
        log.info("Start uploading data chunks");
        final Map<Long, String> spaceMap = getMigratedSpaceMap(gateway, transferId);
        int index = 0;
        for (final Map.Entry<Long, String> space : spaceMap.entrySet()) {
            final long spaceId = space.getKey();
            final String spaceKey = space.getValue();
            PageResponse<Content> pageResponse = null;
            do {
                log.info("Start searching pages for chunk #{} (space {})...", index, spaceKey);
                // https://confluence.atlassian.com/confkb/searching-for-content-with-the-rest-api-and-cql-always-limits-results-to-50-1032258424.html
                pageResponse = cqlSearchService.searchContent(
                        "macro = \"markdown-from-url\" AND space = " + spaceKey,
                        nextPage(pageResponse)
                );
                final List<PageData> pageDataList = buildPageDataList(gateway, transferId, spaceId, pageResponse);

                try {
                    uploadDataPayload(spaceKey, pageDataList, index);
                } catch (Exception e) {
                    logErrorMigrationDetails("Error while preparing migration payload #" + index, e);
                    uploadErrorPayload(index, e);
                }
                index++;
            } while (hasMore(pageResponse));
        }
        return index;
    }

    private PageRequest nextPage(PageResponse<Content> pageResponse) {
        final int nextStart = Optional.ofNullable(pageResponse)
                .map(PageResponse::getPageRequest)
                .map(page -> page.getStart() + page.getLimit())
                .orElse(0);
        return new SimplePageRequest(nextStart, CQL_BATCH_SIZE);
    }

    private boolean hasMore(PageResponse<Content> pageResponse) {
        return Optional.ofNullable(pageResponse).map(PageResponse::hasMore).orElse(false);
    }

    private void uploadDataPayload(String spaceKey, List<PageData> pageDataList, int index) throws IOException {
        log.info("Start uploading data payload #{}...", index);
        final ObjectMapper objectMapper = new ObjectMapper();
        final ObjectNode topLevelNode = objectMapper.createObjectNode();
        topLevelNode.put("serverAppVersion", serverAppVersion);
        topLevelNode.put("spaceKey", spaceKey);
        topLevelNode.put("index", index);
        final ArrayNode pagesNode = topLevelNode.putArray("pages");
        final ArrayNode cloudPageIdsNode = topLevelNode.putArray("cloudPageId");

        for (PageData pageData : pageDataList) {
            cloudPageIdsNode.add(pageData.getCloudId());
            final ObjectNode page = pagesNode.addObject();
            page.put("pageId", pageData.getCloudId());
            page.put("accountId", pageData.getCloudUserKey());
        }

        upload(DATA_PAYLOAD, objectMapper.writeValueAsBytes(topLevelNode));
    }

    private void uploadEndingPayload(int totalChunkCount) throws IOException {
        log.info("Start uploading ending payload...");
        final ObjectMapper objectMapper = new ObjectMapper();
        final ObjectNode topLevelNode = objectMapper.createObjectNode();
        topLevelNode.put("totalChunkCount", totalChunkCount);
        upload(ENDING_PAYLOAD, objectMapper.writeValueAsBytes(topLevelNode));
    }

    private void uploadErrorPayload(int index, Exception e) throws IOException {
        log.info("Start uploading error payload...");
        final ObjectMapper objectMapper = new ObjectMapper();
        final ObjectNode topLevelNode = objectMapper.createObjectNode();
        topLevelNode.put("index", index);
        topLevelNode.put("error", "Error while preparing migration payload in server app: " + e.getMessage());
        upload(ERROR_PAYLOAD, objectMapper.writeValueAsBytes(topLevelNode));
    }

    private Map<Long, String> getMigratedSpaceMap(AppCloudMigrationGateway gateway, String transferId) {
        log.info("Start get migrated space...");
        final Set<String> spaceIds = new HashSet<>();
        final PaginatedMapping spaceIterator = gateway.getPaginatedMapping(transferId, "confluence:space", 5000);
        while (spaceIterator.next()) {
            final Map<String, String> mappings = spaceIterator.getMapping();
            spaceIds.addAll(mappings.keySet());
        }
        final Map<Long, String> spaceKeyMap = spaceIds.stream()
                .map(spaceId -> spaceManager.getSpace(Long.parseLong(spaceId)))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Space::getId, Space::getKey));
        log.info("Migrated space keys: {}", String.join(", ", spaceKeyMap.values()));
        return spaceKeyMap;
    }

    private List<PageData> buildPageDataList(AppCloudMigrationGateway gateway, String transferId, long spaceId, PageResponse<Content> pageResponse) {
        log.info("Start build page data list...");
        final Map<String, Content> serverPageById = Optional.ofNullable(pageResponse)
                .map(PageResponse::getResults)
                .orElseGet(Collections::emptyList)
                .stream()
                .collect(Collectors.toMap(page -> String.valueOf(page.getId().asLong()), Function.identity()));

        final Set<String> serverPageIds = serverPageById.keySet();

        final Map<String, String> mappings = ListUtils.partition(new ArrayList<>(serverPageIds), 100).stream()
                .map(HashSet::new)
                .map(subServerPageIds -> gateway.getMappingById(transferId, "confluence:page", subServerPageIds))
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        final List<PageData> pageDataList = mappings.entrySet().stream()
                .map(entry -> {
                    final String serverPageId = entry.getKey();
                    final String cloudPageId = entry.getValue();
                    final Optional<Content> pageOpt = Optional.ofNullable(serverPageById.get(serverPageId));
                    return pageOpt.map(page -> new PageData(serverPageId, cloudPageId, spaceId, page));
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        userService.enrichCloudUser(gateway, transferId, pageDataList);
        return pageDataList;
    }

    private void upload(String label, byte[] data) {
        final RetryPolicy<Object> retryPolicy = RetryPolicy.builder()
                .withMaxRetries(5)
                .withBackoff(2L, 30L, ChronoUnit.SECONDS)
                .onFailedAttempt(failure -> log.warn("Failed to upload app data. Attempt {}", failure.getAttemptCount(), failure.getLastException()))
                .onRetriesExceeded(failure -> log.error("Retries exceeded", failure.getException()))
                .build();

        Failsafe.with(retryPolicy).run(() -> {
            final OutputStream outputStream = gateway.createAppData(transferId, label);
            outputStream.write(data);
            outputStream.close();
        });
    }

    /**
     * Authenticate a random admin user to enable Confluence service APIs.
     */
    private void setAdminUser() {
        ConfluenceUser adminUser = null;

        final Group adminGroup = userAccessor.getGroup("confluence-administrators");
        if (adminGroup != null) {
            final Spliterator<ConfluenceUser> adminUserIterator = Spliterators.spliteratorUnknownSize(
                    userAccessor.getMembers(adminGroup).iterator(),
                    Spliterator.ORDERED
            );
            adminUser = StreamSupport.stream(adminUserIterator, false).findFirst().orElse(null);
        }

        if (adminUser != null) {
            AuthenticatedUserThreadLocal.set(adminUser);
        } else {
            throw new RuntimeException("Please make sure confluence-administrators has at least 1 user");
        }
    }

    private void logMigrationDetails(String message) {
        log.info("{}: \n\t{}\n\t{}\n\t{}\n\t{}\n\t{}\n\t{}\n\t{}",
                message,
                "Name: " + migrationDetails.getName(),
                "Transfer ID: " + transferId,
                "Cloud Url: " + migrationDetails.getCloudUrl(),
                "Migration ID: " + migrationDetails.getMigrationId(),
                "Migration Scope ID: " + migrationDetails.getMigrationScopeId(),
                "Client Key: " + migrationDetails.getConfluenceClientKey(),
                "Created At: " + migrationDetails.getCreatedAt()
        );
    }

    private void logErrorMigrationDetails(String message, Throwable throwable) {
        log.error("{}: \n\t{}\n\t{}\n\t{}\n\t{}\n\t{}\n\t{}\n\t{}",
                message,
                "Name: " + migrationDetails.getName(),
                "Transfer ID: " + transferId,
                "Cloud Url: " + migrationDetails.getCloudUrl(),
                "Migration ID: " + migrationDetails.getMigrationId(),
                "Migration Scope ID: " + migrationDetails.getMigrationScopeId(),
                "Client Key: " + migrationDetails.getConfluenceClientKey(),
                "Created At: " + migrationDetails.getCreatedAt(),
                throwable
        );
    }
}
