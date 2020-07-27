package com.atlassian.plugins.confluence.markdown;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;

import com.atlassian.plugins.confluence.markdown.ext.DevOpsResizableImage.ResizableImage;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.Reference;
import com.vladsch.flexmark.ext.jekyll.tag.JekyllTag;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html.HtmlRenderer.Builder;
import com.vladsch.flexmark.html.HtmlRenderer.HtmlRendererExtension;
import com.vladsch.flexmark.html.LinkResolver;
import com.vladsch.flexmark.html.LinkResolverFactory;
import com.vladsch.flexmark.html.renderer.LinkResolverBasicContext;
import com.vladsch.flexmark.html.renderer.LinkStatus;
import com.vladsch.flexmark.html.renderer.LinkType;
import com.vladsch.flexmark.html.renderer.ResolvedLink;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MarkdownRelativeDevOpsUrls {
    public static String relativePathDataKey = "";
    public static String devOpsUrlDataKey = "";
    public static String importFrom = "";

    public static class DocxLinkResolver implements LinkResolver {
        private Path markdownPath;
        private final String devOpsUrl;

        public DocxLinkResolver(LinkResolverBasicContext context) {
            try {
                markdownPath = Paths.get(relativePathDataKey);
            } catch (Exception e) {
                markdownPath = null;
            }

            devOpsUrl = devOpsUrlDataKey;
        }

        @NotNull
        @Override
        public ResolvedLink resolveLink(@NotNull Node node, @NotNull LinkResolverBasicContext context,
                @NotNull ResolvedLink link) {
            try
            {
                if (node instanceof Link || node instanceof Reference || node instanceof JekyllTag) {
                    return resolveLinkInternal(devOpsUrl.toString(), link);
                }
                if (node instanceof Image || node instanceof ResizableImage) {
                    Integer basePathEnd = importFrom.indexOf("?path=");
                    if (basePathEnd > 0){
                        String baseUrl = importFrom.substring(0, basePathEnd);
                        link = resolveLinkInternal(baseUrl, link);
                        return link.withUrl(link.getUrl() + "&resolveLfs=true&api-version=5.0");
                    } else {
                        return link;
                    }
                }
            }
            catch (Throwable e){}
            return link;
        }

        private ResolvedLink resolveLinkInternal(String baseUrl, ResolvedLink link) {
            String url = link.getUrl();
            System.out.println(link);

            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("ftp://")
                    || url.startsWith("sftp://")) {
                // resolve url, return one of LinkStatus other than LinkStatus.UNKNOWN
                return link.withStatus(LinkStatus.VALID).withUrl(url);
            } else if (url.startsWith("file:/")) {
                // assume it is good
                return link.withStatus(LinkStatus.VALID).withUrl(url);
            } else if (url.startsWith("www.")) {
                // should be prefixed with http://, we will just add it
                return link.withStatus(LinkStatus.INVALID).withUrl("http://" + url);
            } else {
                if (baseUrl.equals("null") || markdownPath.toString() == null) {
                    return link.withStatus(LinkStatus.INVALID);
                }

                // relative paths processed here
                Path joinedPath = MarkdownHelper.combinePaths(markdownPath, url);

                String formatToUrlPath = joinedPath.toString();
                formatToUrlPath = formatToUrlPath.replace("\\", "%2F");

                link = link.withStatus(LinkStatus.VALID).withLinkType(LinkType.LINK)
                        .withUrl(baseUrl + "?path=" + formatToUrlPath);
            }

            return link;
        }

        public static class Factory implements LinkResolverFactory {
            @Nullable
            @Override
            public Set<Class<?>> getAfterDependents() {
                return null;
            }

            @Nullable
            @Override
            public Set<Class<?>> getBeforeDependents() {
                return null;
            }

            @Override
            public boolean affectsGlobalScope() {
                return false;
            }

            @NotNull
            @Override
            public LinkResolver apply(@NotNull LinkResolverBasicContext context) {
                return new DocxLinkResolver(context);
            }
        }
    }

    static class CustomExtension implements HtmlRendererExtension {
        @Override
        public void rendererOptions(@NotNull MutableDataHolder options) {

        }

        @Override
        public void extend(@NotNull Builder htmlRendererBuilder, @NotNull String rendererType) {
            htmlRendererBuilder.linkResolverFactory(new DocxLinkResolver.Factory());
        }

        public static CustomExtension create() {
            return new CustomExtension();
        }
    }
}