package com.atlassian.plugins.confluence.markdown;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext;
import com.atlassian.plugins.confluence.markdown.configuration.MacroConfigModel;
import com.atlassian.plugins.confluence.markdown.configuration.MarkdownConfigModel;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.atlassian.plugins.confluence.markdown.configuration.PluginAdminGetConfigurationAction.PLUGIN_CONFIG_KEY;


public class MarkdownHelper 
{
    private static ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
        relativePathStr = relativePathStr.substring(2);
        Path relativePath = Paths.get(relativePathStr);

        mainPath = mainPath.getParent();

        return mainPath.resolve(relativePath);
    }

    public static MarkdownConfigModel GetMarkdownConfig(BandanaManager bandanaManager, 
                                                                ConfluenceBandanaContext context){
		MacroConfigModel model = new MacroConfigModel();
        String config = (String) bandanaManager.getValue(context, PLUGIN_CONFIG_KEY);
        if (config != null) {
            try {
                model = objectMapper.readValue(config, MacroConfigModel.class);
            } catch (IOException e) {
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