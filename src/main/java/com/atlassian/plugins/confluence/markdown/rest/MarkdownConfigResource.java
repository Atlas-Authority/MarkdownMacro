package com.atlassian.plugins.confluence.markdown.rest;

import com.atlassian.plugins.confluence.markdown.configuration.MarkdownConfigStore;
import com.atlassian.sal.api.component.ComponentLocator;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.user.UserManager;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
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

    private final PluginSettingsFactory pluginSettingsFactory;
    private final UserManager userManager;
    private final ObjectMapper mapper;

    // Jersey/HK2 needs a public no-arg constructor
    public MarkdownConfigResource() {
        this.pluginSettingsFactory = ComponentLocator.getComponent(PluginSettingsFactory.class);
        this.userManager          = ComponentLocator.getComponent(UserManager.class);
        // Jackson 3: immutable mapper; keep Jackson 2 defaults. Jackson 2's setSerializationInclusion(NON_NULL)
        // set both value and content inclusion, so mirror that here.
        this.mapper = JsonMapper.builder(JsonFactory.builderWithJackson2Defaults().build())
                .configureForJackson2()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .changeDefaultPropertyInclusion(incl ->
                        JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                .build();
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

    // ---- endpoints ----

    @GET
    public Response get() {
        // PluginSettings first; if missing, fall back to the legacy namespace and migrate
        String json = MarkdownConfigStore.readConfigJson(pluginSettingsFactory);
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

            MarkdownConfigStore.writeConfigJson(pluginSettingsFactory, normalized);

            return Response.ok(normalized, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            return Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\":\"Invalid JSON payload\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }
    }
}
