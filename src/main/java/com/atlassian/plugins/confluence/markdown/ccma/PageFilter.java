package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.content.render.xhtml.DefaultConversionContext;
import com.atlassian.confluence.content.render.xhtml.XhtmlException;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.plugin.spring.scanner.annotation.imports.ConfluenceImport;
import com.atlassian.renderer.RenderContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

@Named
class PageFilter {
    private static final Logger log = LoggerFactory.getLogger(PageFilter.class);

    private final XhtmlContent xhtmlContent;

    @Inject
    PageFilter(@ConfluenceImport XhtmlContent xhtmlContent) {
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
                log.error("Unable to parse content body", e);
                result = false;
            }
            return result;
        }
    }
}


