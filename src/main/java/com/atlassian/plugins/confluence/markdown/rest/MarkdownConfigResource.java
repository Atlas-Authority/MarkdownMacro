package com.atlassian.plugins.confluence.markdown.rest;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext;
import com.atlassian.sal.api.component.ComponentLocator;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.user.UserManager;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * REST v2 resource for Markdown Macro configuration.
 * GET returns the stored JSON blob (or {"config":{}} if none).
 * PUT accepts a JSON blob and stores it. Only Confluence admins may PUT.
 *
 * Path (as declared in atlassian-plugin.xml):
 *   /rest/markdown-from-url/1.0/config
 */
@Path("/config")
@Consumes(MediaType.APPLICATION_JSON)
public class MarkdownConfigResource {

    private static final String PS_NAMESPACE = "com.atlassian.plugins.confluence.markdown";
    private static final String PLUGIN_CONFIG_KEY = "markdown-plugin-config-00";
    private static final ConfluenceBandanaContext BANDANA_CTX =
            new ConfluenceBandanaContext("markdown-plugin");

    private final PluginSettingsFactory pluginSettingsFactory;
    private final BandanaManager bandanaManager;
    private final UserManager userManager;
    private final ObjectMapper mapper;

    // Jersey/HK2 needs a public no-arg constructor
    public MarkdownConfigResource() {
        this.pluginSettingsFactory = ComponentLocator.getComponent(PluginSettingsFactory.class);
        this.bandanaManager       = ComponentLocator.getComponent(BandanaManager.class);
        this.userManager          = ComponentLocator.getComponent(UserManager.class);
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    // ---- helpers ----

    private void ensureAdmin() {
        String user = userManager.getRemoteUsername();
        if (user == null) {
            throw new WebApplicationException(Status.UNAUTHORIZED); // 401
        }
        if (!(userManager.isSystemAdmin(user) || userManager.isAdmin(user))) {
            throw new WebApplicationException(Status.FORBIDDEN); // 403
        }
    }

    /** Read from PluginSettings; if missing, fall back to Bandana and migrate. */
    private String readConfigWithMigration() {
        PluginSettings ps = pluginSettingsFactory.createSettingsForKey(PS_NAMESPACE);
        Object v = ps.get(PLUGIN_CONFIG_KEY);
        if (v instanceof String s && !s.isEmpty()) {
            return s;
        }

        // Fallback to Bandana, then migrate forward
        Object legacy = bandanaManager.getValue(BANDANA_CTX, PLUGIN_CONFIG_KEY);
        if (legacy instanceof String s && !s.isBlank() && !"null".equalsIgnoreCase(s)) {
            ps.put(PLUGIN_CONFIG_KEY, s); // migrate
            return s;
        }

        return null;
    }

    // ---- endpoints ----

    @GET
    public Response get() {
        String json = readConfigWithMigration();
        if (json == null) {
            // Return a stable default shape
            json = "{\"config\":{}}";
        }
        return Response.ok(json, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @PUT
    public Response put(String body) {
        ensureAdmin(); // writes require admin

        try {
            // Validate it’s JSON and normalize formatting
            Object tree = mapper.readTree(body);
            String normalized = mapper.writeValueAsString(tree);

            PluginSettings ps = pluginSettingsFactory.createSettingsForKey(PS_NAMESPACE);
            ps.put(PLUGIN_CONFIG_KEY, normalized);

            return Response.ok(normalized, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            return Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\":\"Invalid JSON payload\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }
    }
}
