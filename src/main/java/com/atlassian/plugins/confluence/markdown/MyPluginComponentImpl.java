package com.atlassian.plugins.confluence.markdown;

import com.atlassian.confluence.content.render.xhtml.DefaultConversionContext;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.xhtml.api.MacroDefinition;
import com.atlassian.confluence.xhtml.api.MacroDefinitionHandler;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.migration.app.tracker.*;
import com.atlassian.plugin.spring.scanner.annotation.imports.ConfluenceImport;
import com.atlassian.renderer.RenderContext;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.atlassian.migration.app.tracker.AccessScope.*;

@Named
public class MyPluginComponentImpl implements CloudMigrationListenerV1, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(MyPluginComponentImpl.class);
    private final CloudMigrationAccessor accessor;
    @ConfluenceImport private final PageManager pageManager;
    @ConfluenceImport private final XhtmlContent xhtmlContent;

    @Inject
    public MyPluginComponentImpl(
            LocalCloudMigrationAccessor accessor,
            @ConfluenceImport PageManager pageManager,
            @ConfluenceImport XhtmlContent xhtmlContent)
    {
        // It is not safe to save a direct reference to the gateway as that can change over time
        this.accessor = accessor.getCloudMigrationAccessor();
        this.pageManager = pageManager;
        this.xhtmlContent = xhtmlContent;
    }

    @Override
    public void afterPropertiesSet() {
        this.accessor.registerListener(this);
    }

    @Override
    public void destroy() {
        this.accessor.deregisterListener(this);
    }

    @Override
    public void onStartAppMigration(String transferId, MigrationDetailsV1 migrationDetails) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            ObjectMapper overallMapper = new ObjectMapper();
            ObjectNode topLevelNode = overallMapper.createObjectNode();

            ArrayNode cloudPageIdsNode = topLevelNode.putArray("cloudPageIds");
            log.info("Migration context summary: " + objectMapper.writeValueAsString(migrationDetails));
            PaginatedMapping paginatedMapping = accessor.getCloudMigrationGateway().getPaginatedMapping(transferId, "confluence:page", 5);
            while (paginatedMapping.next()) {
                Map<String, String> mappings = paginatedMapping.getMapping();
                log.info("mappings = {}", objectMapper.writeValueAsString(mappings));

                for (Map.Entry<String, String> entry : mappings.entrySet()) {
                    String serverPageId = entry.getKey();
                    String cloudPageId = entry.getValue();

                    Page page = pageManager.getPage(Long.parseLong(serverPageId));
                    if (page != null && page.isLatestVersion()) {
                        final Boolean[] hasRelevantMacros = {false};

                        String body = page.getBodyAsString();
                        xhtmlContent.handleMacroDefinitions(body, new DefaultConversionContext(new RenderContext()), new MacroDefinitionHandler() {
                            @Override
                            public void handle(MacroDefinition macroDefinition) {
                                if ("markdown-from-url".equals(macroDefinition.getName())) {
                                    hasRelevantMacros[0] = true;
                                }
                            }
                        });

                        if (hasRelevantMacros[0]) {
                            cloudPageIdsNode.add(cloudPageId);
                        }
                    }
                }
            }

            OutputStream stream = accessor.getCloudMigrationGateway().createAppData(transferId);
            stream.write(overallMapper.writeValueAsString(topLevelNode).getBytes(StandardCharsets.UTF_8));
            stream.close();

        } catch (Exception e) {
            log.error("Error while running app migration", e);
        }
    }

    @Override
    public void onRegistrationAccepted() {

    }

    @Override
    public void onRegistrarRemoved() {

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
