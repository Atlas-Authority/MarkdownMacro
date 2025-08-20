package com.atlassian.plugins.confluence.markdown.configuration;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugin.spring.scanner.annotation.imports.ConfluenceImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import javax.inject.Inject;

public class PluginAdminSaveConfigurationAction extends ConfluenceActionSupport {
    public static final String PLUGIN_CONFIG_KEY = "markdown-plugin-config-00";
    private static final String PS_NAMESPACE = "com.atlassian.plugins.confluence.markdown";

    // New store for Confluence 10+
    private final PluginSettingsFactory pluginSettingsFactory;

    // Legacy store (read-only fallback so we can compare with old config if present)
    private final BandanaManager bandanaManager;

    private final ConfluenceBandanaContext context = new ConfluenceBandanaContext("markdown-plugin");
    private final ObjectMapper objectMapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MacroConfigModel model;

    @Inject
    public PluginAdminSaveConfigurationAction(
            @ComponentImport PluginSettingsFactory pluginSettingsFactory,
            @ConfluenceImport BandanaManager bandanaManager
    ) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.bandanaManager = bandanaManager;
    }

    @Override
    public String execute() throws Exception {
        // Load previous config (from PluginSettings first, Bandana as fallback) to set isChanged flag
        String oldJson = readExistingConfigJson();
        MacroConfigModel oldModel = parseOrDefault(oldJson);

        if (model != null) {
            boolean changed = (oldModel.getConfig() == null && model.getConfig() != null)
                    || (oldModel.getConfig() != null && !oldModel.getConfig().equals(model.getConfig()));
            model.setIsChanged(changed);
        }

        // SAVE to PluginSettings (Bandana write removed in Conf 10)
        PluginSettings ps = pluginSettingsFactory.createSettingsForKey(PS_NAMESPACE);
        ps.put(PLUGIN_CONFIG_KEY, objectMapper.writeValueAsString(model));

        return SUCCESS;
    }

    @StrutsParameter
    public void setData(String data) throws Exception {
        model = objectMapper.readValue(data, MacroConfigModel.class);
    }

    private String readExistingConfigJson() {
        // Prefer the new store
        PluginSettings ps = pluginSettingsFactory.createSettingsForKey(PS_NAMESPACE);
        Object v = ps.get(PLUGIN_CONFIG_KEY);
        if (v instanceof String && !((String) v).trim().isEmpty()) {
            return (String) v;
        }

        // Fallback to legacy Bandana (read only)
        Object legacy = bandanaManager.getValue(context, PLUGIN_CONFIG_KEY);
        return (legacy instanceof String) ? (String) legacy : null;
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
