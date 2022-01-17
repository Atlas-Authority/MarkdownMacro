package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.api.model.content.Content;
import com.atlassian.confluence.api.model.content.ContentBody;
import com.atlassian.confluence.api.model.content.ContentRepresentation;
import com.atlassian.confluence.api.model.content.ContentType;
import com.atlassian.confluence.api.model.content.id.ContentId;
import com.atlassian.confluence.api.service.content.ContentService;
import com.atlassian.confluence.content.render.xhtml.DefaultConversionContext;
import com.atlassian.confluence.rest.api.model.ExpansionsParser;
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
import com.atlassian.renderer.RenderContext;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.atlassian.migration.app.AccessScope.APP_DATA_OTHER;
import static com.atlassian.migration.app.AccessScope.MIGRATION_TRACING_PRODUCT;

@Named
@ExportAsService
public class MyPluginComponentImpl implements DiscoverableListener {

    private static final Logger log = LoggerFactory.getLogger(MyPluginComponentImpl.class);
    @ConfluenceImport private final ContentService contentService;
    @ConfluenceImport private final UserAccessor userAccessor;
    @ConfluenceImport private final XhtmlContent xhtmlContent;

    @Inject
    public MyPluginComponentImpl(
            @ConfluenceImport ContentService contentService,
            @ConfluenceImport UserAccessor userAccessor,
            @ConfluenceImport XhtmlContent xhtmlContent)
    {
        // It is not safe to save a direct reference to the gateway as that can change over time
        this.contentService = contentService;
        this.userAccessor = userAccessor;
        this.xhtmlContent = xhtmlContent;
    }

    @Override
    public void onStartAppMigration(AppCloudMigrationGateway gateway, String transferId, MigrationDetailsV1 migrationDetails) {
        try(final OutputStream stream = gateway.createAppData(transferId)) {
            final boolean isSetupAdminUserSuccess = setupAdminUser();
            if (!isSetupAdminUserSuccess) {
                throw new RuntimeException("Please make sure confluence-administrators has at least 1 user");
            }

            ObjectMapper objectMapper = new ObjectMapper();
            ObjectMapper overallMapper = new ObjectMapper();
            ObjectNode topLevelNode = overallMapper.createObjectNode();

            ArrayNode cloudPageIdsNode = topLevelNode.putArray("cloudPageIds");
            log.info("Migration context summary: " + objectMapper.writeValueAsString(migrationDetails));
            PaginatedMapping paginatedMapping = gateway.getPaginatedMapping(transferId, "confluence:page", 5);
            while (paginatedMapping.next()) {
                Map<String, String> mappings = paginatedMapping.getMapping();
                log.info("mappings = {}", objectMapper.writeValueAsString(mappings));

                for (Map.Entry<String, String> entry : mappings.entrySet()) {
                    String serverPageId = entry.getKey();
                    String cloudPageId = entry.getValue();

                    final Optional<String> pageBodyOpt = getLatestPageBody(Long.parseLong(serverPageId));

                    if (pageBodyOpt.isPresent()) {
                        xhtmlContent.handleMacroDefinitions(pageBodyOpt.get(), new DefaultConversionContext(new RenderContext()), macroDefinition -> {
                            if ("markdown-from-url".equals(macroDefinition.getName())) {
                                cloudPageIdsNode.add(cloudPageId);
                            }
                        });
                    }
                }
            }

            stream.write(overallMapper.writeValueAsString(topLevelNode).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error while running app migration", e);
        }
    }

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

    private Optional<String> getLatestPageBody(Long pageId) {
        final Optional<Content> pageOpt = contentService
                .find(ExpansionsParser.parse("history,body.storage"))
                .withType(ContentType.PAGE)
                .withId(ContentId.of(pageId))
                .fetch();
        return pageOpt
                .filter(page -> page.getHistory().isLatest())
                .map(Content::getBody)
                .map(page -> page.get(ContentRepresentation.STORAGE))
                .map(ContentBody::getValue);
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
        return Stream.of(
                APP_DATA_OTHER,
                MIGRATION_TRACING_PRODUCT)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
