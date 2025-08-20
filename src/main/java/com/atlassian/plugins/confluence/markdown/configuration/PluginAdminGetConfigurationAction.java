package com.atlassian.plugins.confluence.markdown.configuration;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugin.spring.scanner.annotation.imports.ConfluenceImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;

public class PluginAdminGetConfigurationAction extends ConfluenceActionSupport {
    // Keys/namespaces
    public static final String PLUGIN_CONFIG_KEY = "markdown-plugin-config-00";
    private static final String PS_NAMESPACE = "com.atlassian.plugins.confluence.markdown";

    // New store (Conf 10+)
    private final PluginSettingsFactory pluginSettingsFactory;

    // Legacy store (read + migrate)
    private final BandanaManager bandanaManager;
    private final ConfluenceBandanaContext context = new ConfluenceBandanaContext("markdown-plugin");

    private final ObjectMapper objectMapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MacroConfigModel model;

    @Inject
    public PluginAdminGetConfigurationAction(
            @ComponentImport PluginSettingsFactory pluginSettingsFactory,
            @ConfluenceImport BandanaManager bandanaManager
    ) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.bandanaManager = bandanaManager;
    }

    @Override
    public String doDefault() throws Exception {
        String json = getConfigWithMigration(); // PluginSettings first; migrate from Bandana if needed
        model = parseOrDefault(json);
        return INPUT;
    }

    /** Velocity calls this to render initial state */
    public String getData() throws JsonProcessingException {
        return objectMapper.writeValueAsString(model);
    }

    // ----------------- helpers -----------------

    /**
     * Reads config from PluginSettings. If not present, reads from Bandana and migrates it
     * to PluginSettings (so future reads are from the new store).
     */
    private String getConfigWithMigration() {
        // 1) Try PluginSettings first
        PluginSettings ps = pluginSettingsFactory.createSettingsForKey(PS_NAMESPACE);
        Object v = ps.get(PLUGIN_CONFIG_KEY);
        if (v instanceof String && !((String) v).trim().isEmpty() && !"null".equalsIgnoreCase((String) v)) {
            return (String) v;
        }

        // 2) Fallback to Bandana (legacy) and migrate if found
        Object legacy = bandanaManager.getValue(context, PLUGIN_CONFIG_KEY);
        if (legacy instanceof String) {
            String legacyJson = ((String) legacy).trim();
            if (!legacyJson.isEmpty() && !"null".equalsIgnoreCase(legacyJson)) {
                // migrate to new store
                ps.put(PLUGIN_CONFIG_KEY, legacyJson);
                return legacyJson;
            }
        }

        return null;
    }

    private MacroConfigModel parseOrDefault(String json) {
        try {
            if (json != null && !json.trim().isEmpty() && !"null".equalsIgnoreCase(json)) {
                return objectMapper.readValue(json, MacroConfigModel.class);
            }
        } catch (Exception ignore) { /* fall through to default */ }
        return new MacroConfigModel();
    }
}
