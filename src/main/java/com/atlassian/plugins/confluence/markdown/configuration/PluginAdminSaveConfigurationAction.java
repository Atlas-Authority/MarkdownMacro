package com.atlassian.plugins.confluence.markdown.configuration;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@Scanned
public class PluginAdminSaveConfigurationAction extends ConfluenceActionSupport {
    public static final String PLUGIN_CONFIG_KEY = "markdown-plugin-config-00";

    private BandanaManager bandanaManager;
    private ConfluenceBandanaContext context = new ConfluenceBandanaContext("markdown-plugin");
    private ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MacroConfigModel model;

    @Override
    public String execute() throws Exception {
    	String oldConfig = (String) bandanaManager.getValue(context, PLUGIN_CONFIG_KEY);
        MacroConfigModel oldModel = new MacroConfigModel();
        if (oldConfig!= null && !oldConfig.trim().isEmpty() && !"null".equalsIgnoreCase(oldConfig)) {
        	oldModel = objectMapper.readValue(oldConfig, MacroConfigModel.class);
        }
        if (oldModel == null) {
        	oldModel = new MacroConfigModel();
        }
    	
        if (model != null) {
        	if (!oldModel.getConfig().equals(model.getConfig())) model.setIsChanged(true);
        	else model.setIsChanged(false);
        }
    	
        bandanaManager.setValue(context, PLUGIN_CONFIG_KEY, objectMapper.writeValueAsString(model));
        return SUCCESS;
    }

    public void setBandanaManager(BandanaManager bandanaManager) {
        this.bandanaManager = bandanaManager;
    }

    public void setData(String data) throws Exception {
        model = objectMapper.readValue(data, MacroConfigModel.class);
    }
}
