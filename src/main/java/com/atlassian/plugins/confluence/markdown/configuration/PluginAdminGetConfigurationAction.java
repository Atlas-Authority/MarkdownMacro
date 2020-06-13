package com.atlassian.plugins.confluence.markdown.configuration;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@Scanned
public class PluginAdminGetConfigurationAction extends ConfluenceActionSupport {
    public static final String PLUGIN_CONFIG_KEY = "markdown-plugin-config-00";

    private BandanaManager bandanaManager;
    private ConfluenceBandanaContext context = new ConfluenceBandanaContext("markdown-plugin");
    private ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    
    private MacroConfigModel model;

    @Override
    public String doDefault() throws Exception {
        String config = (String) bandanaManager.getValue(context, PLUGIN_CONFIG_KEY);
        model = new MacroConfigModel();
        if (config != null && !config.trim().isEmpty() && !"null".equalsIgnoreCase(config)) {
            model = objectMapper.readValue(config, MacroConfigModel.class);
        }
        if (model == null) {
            model = new MacroConfigModel();
        }
        return INPUT;
    }

    public void setBandanaManager(BandanaManager bandanaManager) {
        this.bandanaManager = bandanaManager;
    }

    public String getData() throws JsonProcessingException {
        return objectMapper.writeValueAsString(model);
    }
}
