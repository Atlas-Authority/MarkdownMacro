package com.atlassian.plugins.confluence.markdown;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.content.render.xhtml.DefaultConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.definition.DefinitionExtension;
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughSubscriptExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.ins.InsExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension;
import com.vladsch.flexmark.ext.youtube.embedded.YouTubeLinkExtension;
import com.vladsch.flexmark.ext.toc.TocExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.superscript.SuperscriptExtension;
import com.vladsch.flexmark.util.options.MutableDataSet;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.*;


public class MarkdownMacro extends BaseMacro implements Macro {

    private final XhtmlContent xhtmlUtils;

    private PageBuilderService pageBuilderService;

    @Autowired
    public MarkdownMacro(@ComponentImport PageBuilderService pageBuilderService, XhtmlContent xhtmlUtils) {
        this.pageBuilderService = pageBuilderService;
        this.xhtmlUtils = xhtmlUtils;
    }

    @Override
    public BodyType getBodyType() {
        return BodyType.PLAIN_TEXT;
    }

    @Override
    public OutputType getOutputType() {
        return OutputType.BLOCK;
    }

    @Override
    public String execute(Map<String, String> parameters, String bodyContent, ConversionContext conversionContext) throws MacroExecutionException {


        pageBuilderService.assembler().resources().requireWebResource("com.atlassian.plugins.confluence.markdown.confluence-markdown-macro:highlightjs");

        MutableDataSet options = new MutableDataSet()
                .set(HtmlRenderer.GENERATE_HEADER_ID, true)
                .set(HtmlRenderer.INDENT_SIZE, 2)
                .set(HtmlRenderer.PERCENT_ENCODE_URLS, true)
                .set(HtmlRenderer.ESCAPE_HTML, true)
                // for full GFM table compatibility add the following table extension options:

                .set(TablesExtension.COLUMN_SPANS, true)
                .set(TablesExtension.APPEND_MISSING_COLUMNS, true)
                .set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
                .set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)
                .set(TablesExtension.CLASS_NAME, "confluenceTable")
                .set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()));

        options.set(Parser.EXTENSIONS, Arrays.asList(
                TablesExtension.create(),
                StrikethroughSubscriptExtension.create(),
                InsExtension.create(),
                TaskListExtension.create(),
                FootnoteExtension.create(),
                WikiLinkExtension.create(),
                DefinitionExtension.create(),
                AnchorLinkExtension.create(),
                AutolinkExtension.create(),
                SuperscriptExtension.create(),
                YouTubeLinkExtension.create(),
                TocExtension.create()

        ));


        String highlightjs = "<script>\n" +
                "AJS.$('[data-macro-name=\"markdown\"] code').each(function(i, block) {\n" +
                "    hljs.highlightBlock(block);\n" +
                "  });\n" +
                "</script>";

        String highlightjscss = "<style>\n" +
                ".hljs {display: inline;}\n" +
                "pre > code {display: block !important;}\n" +
                "</style>";

        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();

        Node document = parser.parse(bodyContent);

        String htmlBody = renderer.render(document);

        Document doc = Jsoup.parseBodyFragment(htmlBody);
        Element body = doc.body();
        Elements th = body.getElementsByTag("th");
        Elements td = body.getElementsByTag("td");
        for (Element thi : th) {
            thi.addClass("confluenceTh");
        }
        for (Element tdi : td) {
            tdi.addClass("confluenceTd");
        }

        String html =  body.html() +  highlightjs + highlightjscss;

        return html;

    }

    @Override
    public boolean hasBody() {
        return true;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public RenderMode getBodyRenderMode() {
        return RenderMode.NO_RENDER;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String execute(Map map, String s, RenderContext renderContext) throws MacroException {
        try {
            return execute(map, s, new DefaultConversionContext(renderContext));
        } catch (MacroExecutionException e) {
            throw new MacroException(e.getMessage(), e);
        }
    }
}
