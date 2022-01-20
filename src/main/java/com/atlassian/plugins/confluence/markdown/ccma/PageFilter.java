package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.content.render.xhtml.DefaultConversionContext;
import com.atlassian.confluence.content.render.xhtml.XhtmlException;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.renderer.RenderContext;

class PageFilter {
    private final XhtmlContent xhtmlContent;

    PageFilter(XhtmlContent xhtmlContent) {
        this.xhtmlContent = xhtmlContent;
    }

    boolean hasMarkdownMacroFromUrl(String body){
        return new PageChecker().check(xhtmlContent, body);
    }

    private static class PageChecker {
        boolean result = false;

        boolean check(XhtmlContent xhtmlContent, String body) {
            try {
                xhtmlContent.handleMacroDefinitions(body, new DefaultConversionContext(
                        new RenderContext()),
                        macroDefinition -> result = "markdown-from-url".equals(macroDefinition.getName()
                        ));
            } catch (XhtmlException e) {
                result = false;
            }
            return result;
        }
    }
}


