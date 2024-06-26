package com.atlassian.plugins.confluence.markdown.configuration;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext;
import com.atlassian.plugin.spring.scanner.annotation.imports.ConfluenceImport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import javax.inject.Inject;

public class PluginAdminSaveConfigurationAction extends ConfluenceActionSupport {
    public static final String PLUGIN_CONFIG_KEY = "markdown-plugin-config-00";

    private final BandanaManager bandanaManager;
    private final ConfluenceBandanaContext context = new ConfluenceBandanaContext("markdown-plugin");
    private final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MacroConfigModel model;

    @Inject
    public PluginAdminSaveConfigurationAction(@ConfluenceImport BandanaManager bandanaManager) {
        this.bandanaManager = bandanaManager;
    }

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

    @StrutsParameter
    public void setData(String data) throws Exception {
        model = objectMapper.readValue(data, MacroConfigModel.class);
    }
}
