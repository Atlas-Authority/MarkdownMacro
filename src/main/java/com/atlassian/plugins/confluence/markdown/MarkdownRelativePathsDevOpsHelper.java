package com.atlassian.plugins.confluence.markdown;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarkdownRelativePathsDevOpsHelper {
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


    public static boolean IsNullOrEmpty(String str)
    {
        return str == null || str.isEmpty();
    }
}