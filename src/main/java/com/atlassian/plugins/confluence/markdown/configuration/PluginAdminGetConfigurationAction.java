package com.atlassian.plugins.confluence.markdown.configuration;

import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import jakarta.inject.Inject;

public class PluginAdminGetConfigurationAction extends ConfluenceActionSupport {
    // Keys/namespaces
    public static final String PLUGIN_CONFIG_KEY = MarkdownConfigStore.CONFIG_KEY;

    // New store (Conf 10+); legacy namespace is read + migrated by MarkdownConfigStore
    private final PluginSettingsFactory pluginSettingsFactory;

    // Jackson 3: immutable mapper; keep Jackson 2 defaults so persisted config JSON reads/writes as before
    private final ObjectMapper objectMapper = JsonMapper.builder(JsonFactory.builderWithJackson2Defaults().build())
            .configureForJackson2()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private MacroConfigModel model;

    @Inject
    public PluginAdminGetConfigurationAction(
            @ComponentImport PluginSettingsFactory pluginSettingsFactory
    ) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public String doDefault() throws Exception {
        String json = MarkdownConfigStore.readConfigJson(pluginSettingsFactory); // PluginSettings first; migrate legacy if needed
        model = parseOrDefault(json);
        return INPUT;
    }

    /** Velocity calls this to render initial state */
    public String getData() throws JacksonException {
        return objectMapper.writeValueAsString(model);
    }

    // ----------------- helpers -----------------

    private MacroConfigModel parseOrDefault(String json) {
        try {
            if (json != null && !json.trim().isEmpty() && !"null".equalsIgnoreCase(json)) {
                return objectMapper.readValue(json, MacroConfigModel.class);
            }
        } catch (Exception ignore) { /* fall through to default */ }
        return new MacroConfigModel();
    }
}
