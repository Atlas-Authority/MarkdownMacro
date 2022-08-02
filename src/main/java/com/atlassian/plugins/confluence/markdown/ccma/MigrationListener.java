package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.api.service.search.CQLSearchService;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.migration.app.*;
import com.atlassian.migration.app.gateway.AppCloudMigrationGateway;
import com.atlassian.migration.app.gateway.MigrationDetailsV1;
import com.atlassian.migration.app.listener.DiscoverableListener;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ConfluenceImport;
import org.springframework.beans.factory.annotation.Value;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

import static com.atlassian.migration.app.AccessScope.*;

@Named
@ExportAsService
public class MigrationListener implements DiscoverableListener {

    private final SpaceManager spaceManager;
    private final CQLSearchService cqlSearchService;
    private final UserAccessor userAccessor;
    private final String serverAppVersion;
    private final UserService userService;

    @Inject
    public MigrationListener(
            @Value("${build.version}") String serverAppVersion,
            @ConfluenceImport SpaceManager spaceManager,
            @ConfluenceImport CQLSearchService cqlSearchService,
            @ConfluenceImport UserAccessor userAccessor,
            UserService userService
    ) {
        this.spaceManager = spaceManager;
        this.cqlSearchService = cqlSearchService;
        this.userAccessor = userAccessor;
        this.serverAppVersion = serverAppVersion;
        this.userService = userService;
    }

    @Override
    public void onStartAppMigration(AppCloudMigrationGateway gateway, String transferId, MigrationDetailsV1 migrationDetails) {
        new Migrator(
                spaceManager,
                cqlSearchService,
                userAccessor,
                serverAppVersion,
                userService,
                gateway,
                transferId,
                migrationDetails
        ).migrate();
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
