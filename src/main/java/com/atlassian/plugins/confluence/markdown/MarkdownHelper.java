package com.atlassian.plugins.confluence.markdown;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.atlassian.plugins.confluence.markdown.configuration.MacroConfigModel;
import com.atlassian.plugins.confluence.markdown.configuration.MarkdownConfigStore;
import com.atlassian.plugins.confluence.markdown.configuration.MarkdownConfigModel;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;


public class MarkdownHelper 
{
    // Jackson 3: immutable mapper; keep Jackson 2 defaults so persisted config JSON reads/writes as before
    private static ObjectMapper objectMapper = JsonMapper.builder(JsonFactory.builderWithJackson2Defaults().build())
            .configureForJackson2()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static Path getPathFromRawMarkdownUrl(URL url) {
        String strUrl = url.toString();
        int start = strUrl.indexOf("path") + 5;
        int end = strUrl.indexOf(".md", start) + 3;

        String extractedPath = strUrl.substring(start, end);
        return Paths.get(extractedPath.replace("%2F", "/"));
    }

    public static Path getPathFromRawMarkdownUrlAsBodyContent(String strUrl) {
        int start = strUrl.indexOf("path") + 5;
        int end = strUrl.indexOf(".md", start) + 3;

        String extractedPath = strUrl.substring(start, end);
        return Paths.get(extractedPath.replace("%2F", "/"));
    }

    public static Path combinePaths(Path mainPath, String relativePathStr){
        // if starts with '/', then it is path from repo-root
        if (relativePathStr.charAt(0) == '/') {
            return Paths.get(relativePathStr).normalize();
        }

        // otherwise resolve and normalize
        Path relativePath = Paths.get(relativePathStr);
        mainPath = mainPath.getParent();
        return mainPath.resolve(relativePath).normalize();
    }

    public static MarkdownConfigModel GetMarkdownConfig(PluginSettingsFactory pluginSettingsFactory){
		MacroConfigModel model = new MacroConfigModel();
        String config = MarkdownConfigStore.readConfigJson(pluginSettingsFactory);
        if (config != null) {
            try {
                model = objectMapper.readValue(config, MacroConfigModel.class);
            } catch (JacksonException e) {
                throw new RuntimeException(e);
            }
		}
		
		return model.getConfig();
	}

    public static boolean isNullOrEmpty(String str)
    {
        return str == null || str.isEmpty();
    }
}   