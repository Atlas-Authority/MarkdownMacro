package com.atlassian.plugins.confluence.markdown.configuration;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext;
import com.atlassian.plugin.spring.scanner.annotation.imports.ConfluenceImport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@Path("/config")
public class PluginAdminGetConfigurationAction extends ConfluenceActionSupport {
    public static final String PLUGIN_CONFIG_KEY = "markdown-plugin-config-00";

    @ConfluenceImport
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

    @Path("/retrieve")
    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response getMarkdownConfig()
    {
        String config = (String) bandanaManager.getValue(context, PLUGIN_CONFIG_KEY);

        model = new MacroConfigModel();
        if (config != null && !config.trim().isEmpty() && !"null".equalsIgnoreCase(config)) {
            try{
                model = objectMapper.readValue(config, MacroConfigModel.class);
            } catch (Exception e) {
                model = new MacroConfigModel();
            }
        }
        if (model == null) {
            model = new MacroConfigModel();
        }

        return Response.ok( model.getConfig().getIsAzureDevOpsEnabled() ).build();
    }
}
