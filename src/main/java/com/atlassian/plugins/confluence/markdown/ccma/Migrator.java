package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.api.model.content.*;
import com.atlassian.confluence.api.model.pagination.PageResponse;
import com.atlassian.confluence.api.service.content.SpaceService;
import com.atlassian.confluence.api.service.search.CQLSearchService;
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
    private static final int FETCH_SPACE_SIZE = 100;
    private static final int GET_MAPPING_SIZE = 5000;
    private static final String STARTING_PAYLOAD = "STARTING_PAYLOAD";
    private static final String DATA_PAYLOAD = "DATA_PAYLOAD";
    private static final String DATA_ERROR_PAYLOAD = "DATA_ERROR_PAYLOAD";
    private static final String ENDING_PAYLOAD = "ENDING_PAYLOAD";

    private final SpaceService spaceService;
    private final CQLSearchService cqlSearchService;
    private final UserAccessor userAccessor;
    private final String serverAppVersion;
    private final UserService userService;

    private final AppCloudMigrationGateway gateway;
    private final String transferId;
    private final MigrationDetailsV1 migrationDetails;

    Migrator(
            SpaceService spaceService,
            CQLSearchService cqlSearchService,
            UserAccessor userAccessor,
            String serverAppVersion,
            UserService userService,
            AppCloudMigrationGateway gateway,
            String transferId,
            MigrationDetailsV1 migrationDetails
    ) {
        this.spaceService = spaceService;
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
            int totalNonEmptyChunks = uploadChunks();
            uploadEndingPayload(totalNonEmptyChunks);
        } catch (Exception e) {
            logErrorMigrationDetails("Error while running app migration", e);
        }
    }

    private void uploadStartingPayload() throws IOException {
        log.info("Start uploading starting payload");
        buildPayloadAndUpload(STARTING_PAYLOAD, objectMapper -> {
            final ObjectNode node = objectMapper.createObjectNode();
            node.put("serverAppVersion", serverAppVersion);
            node.put("sourceApp", "markdown-macro");
            return node;
        });
    }

    private int uploadChunks() throws IOException {
        log.info("Start uploading data chunks");
        final Map<Long, String> spaceMap = getMigratedSpaceMap();
        int index = 0;
        int totalNonEmptyChunks = 0;
        for (final Map.Entry<Long, String> space : spaceMap.entrySet()) {
            final long spaceId = space.getKey();
            final String spaceKey = space.getValue();
            final PageIterable<Content> cqlSearchIterable = PageIterable.cqlSearch(
                    cqlSearchService,
                    String.format("macro = \"markdown-from-url\" AND space = \\\"%s\\\"", spaceKey),
                    CQL_BATCH_SIZE
            );
            for (final PageResponse<Content> pages : cqlSearchIterable) {
                log.info("Start searching pages for chunk #{} (space {})...", index, spaceKey);
                try {
                    final List<PageData> pageDataList = buildPageDataList(spaceId, pages);
                    uploadDataPayload(spaceKey, pageDataList, index);
                    if (pageDataList.size() > 0) {
                        totalNonEmptyChunks++;
                    }
                } catch (Exception e) {
                    logErrorMigrationDetails("Error while preparing migration payload #" + index, e);
                    uploadErrorPayload(index, e);
                }
                index++;
            }
        }
        return totalNonEmptyChunks;
    }

    private void uploadDataPayload(String spaceKey, List<PageData> pageDataList, int index) throws IOException {
        log.info("Start uploading data payload #{}...", index);
        buildPayloadAndUpload(DATA_PAYLOAD + "_" + pageDataList.size(), objectMapper -> {
            final ObjectNode node = objectMapper.createObjectNode();
            node.put("spaceKey", spaceKey);
            final ArrayNode pagesNode = node.putArray("pages");
            final ArrayNode cloudPageIdsNode = node.putArray("cloudPageId");

            for (PageData pageData : pageDataList) {
                cloudPageIdsNode.add(pageData.getCloudId());
                final ObjectNode page = pagesNode.addObject();
                page.put("pageId", pageData.getCloudId());
                page.put("accountId", pageData.getCloudUserKey());
            }

            return node;
        });
    }

    private void uploadEndingPayload(int totalNonEmptyChunks) throws IOException {
        log.info("Start uploading ending payload...");
        buildPayloadAndUpload(ENDING_PAYLOAD + "_" + totalNonEmptyChunks, objectMapper -> {
            final ObjectNode node = objectMapper.createObjectNode();
            node.put("totalNonEmptyChunks", totalNonEmptyChunks);
            return node;
        });
    }

    private void uploadErrorPayload(int index, Exception e) throws IOException {
        log.info("Start uploading error payload #{}...", index);
        buildPayloadAndUpload(DATA_ERROR_PAYLOAD, objectMapper -> {
            final ObjectNode node = objectMapper.createObjectNode();
            node.put("error", "Error while preparing migration payload in server app: " + e.getMessage());
            return node;
        });
    }

    /**
     * Get migrated spaces that are actually exist at the time when migration is triggered.
     * @return a map of space ID to space key
     */
    private Map<Long, String> getMigratedSpaceMap() {
        log.info("Start get migrated space...");

        // Get all migrated spaces
        final Set<String> migratedSpaceIds = new HashSet<>();
        final PaginatedMapping migratedSpaceIterator = gateway.getPaginatedMapping(transferId, "confluence:space", GET_MAPPING_SIZE);
        while (migratedSpaceIterator.next()) {
            final Map<String, String> mappings = migratedSpaceIterator.getMapping();
            migratedSpaceIds.addAll(mappings.keySet());
        }

        // Get all current spaces on server side
        final List<Space> spaces = new ArrayList<>();
        final PageIterable<Space> spaceIterable = PageIterable.spaces(spaceService, FETCH_SPACE_SIZE);
        for (final PageResponse<Space> spacePage : spaceIterable) {
            spaces.addAll(spacePage.getResults());
        }

        // Filter out migrated spaces which actually exist
        final Map<Long, String> spaceKeyMap = spaces.stream()
                .filter(space -> migratedSpaceIds.contains(String.valueOf(space.getId())))
                .collect(Collectors.toMap(Space::getId, Space::getKey, (p1, p2) -> p1));

        log.info("Migrated space keys: {}", String.join(", ", spaceKeyMap.values()));
        return spaceKeyMap;
    }

    /**
     * Build java model of the upload payload.
     */
    private List<PageData> buildPageDataList(long spaceId, PageResponse<Content> pages) {
        log.info("Start build page data list...");
        final Map<String, Content> serverPageById = pages.getResults().stream().collect(Collectors.toMap(
                page -> String.valueOf(page.getId().asLong()),
                Function.identity(),
                (p1, p2) -> p1
        ));

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

    /**
     * Loan pattern to build and upload payload.
     */
    private void buildPayloadAndUpload(String label, Function<ObjectMapper, ObjectNode> payloadBuilder) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();
        final ObjectNode payload = payloadBuilder.apply(objectMapper);
        upload(label, objectMapper.writeValueAsBytes(payload));
    }

    /**
     * Upload to S3.
     */
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
