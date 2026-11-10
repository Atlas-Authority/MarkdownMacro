package com.atlassian.plugins.confluence.markdown.configuration;

import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import jakarta.inject.Inject;

public class PluginAdminSaveConfigurationAction extends ConfluenceActionSupport {
    public static final String PLUGIN_CONFIG_KEY = MarkdownConfigStore.CONFIG_KEY;

    // New store for Confluence 10+ (legacy namespace fallback handled by MarkdownConfigStore)
    private final PluginSettingsFactory pluginSettingsFactory;

    // Jackson 3: immutable mapper; keep Jackson 2 defaults so persisted config JSON reads/writes as before
    private final ObjectMapper objectMapper = JsonMapper.builder(JsonFactory.builderWithJackson2Defaults().build())
            .configureForJackson2()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private MacroConfigModel model;

    @Inject
    public PluginAdminSaveConfigurationAction(
            @ComponentImport PluginSettingsFactory pluginSettingsFactory
    ) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public String execute() throws Exception {
        // Load previous config (from PluginSettings first, legacy namespace as fallback) to set isChanged flag
        String oldJson = MarkdownConfigStore.readConfigJson(pluginSettingsFactory);
        MacroConfigModel oldModel = parseOrDefault(oldJson);

        if (model != null) {
            boolean changed = (oldModel.getConfig() == null && model.getConfig() != null)
                    || (oldModel.getConfig() != null && !oldModel.getConfig().equals(model.getConfig()));
            model.setIsChanged(changed);
        }

        // SAVE to PluginSettings (Bandana write removed in Conf 10)
        MarkdownConfigStore.writeConfigJson(pluginSettingsFactory, objectMapper.writeValueAsString(model));

        return SUCCESS;
    }

    @StrutsParameter
    public void setData(String data) throws Exception {
        model = objectMapper.readValue(data, MacroConfigModel.class);
    }

    private MacroConfigModel parseOrDefault(String json) {
        try {
            if (json != null && !json.trim().isEmpty() && !"null".equalsIgnoreCase(json)) {
                return objectMapper.readValue(json, MacroConfigModel.class);
            }
        } catch (Exception ignore) {}
        return new MacroConfigModel();
    }
}
