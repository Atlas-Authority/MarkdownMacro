package com.atlassian.plugins.confluence.markdown;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.content.render.xhtml.DefaultConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugins.confluence.markdown.ext.DevOpsResizableImage.ResizableImageExtension;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughSubscriptExtension;
import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.definition.DefinitionExtension;
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.ins.InsExtension;
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension;
import com.vladsch.flexmark.ext.youtube.embedded.YouTubeLinkExtension;
import com.vladsch.flexmark.ext.toc.TocExtension;
import com.vladsch.flexmark.ext.superscript.SuperscriptExtension;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.misc.Extension;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;


public class MarkdownMacro extends BaseMacro implements Macro {

    private final XhtmlContent xhtmlUtils;

    private PageBuilderService pageBuilderService;
    private BandanaManager bandanaManager;
    private ConfluenceBandanaContext context = new ConfluenceBandanaContext("markdown-plugin");

    @Autowired
    public MarkdownMacro(@ComponentImport PageBuilderService pageBuilderService, @ComponentImport XhtmlContent xhtmlUtils, @ComponentImport BandanaManager bandanaManager) {
        this.pageBuilderService = pageBuilderService;
        this.xhtmlUtils = xhtmlUtils;
        this.bandanaManager = bandanaManager;
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
       	pageBuilderService.assembler().resources().requireWebResource("com.atlassian.plugins.confluence.markdown.confluence-markdown-macro:mermaidjs");

		MutableDataSet options = new MutableDataSet()
			.set(HtmlRenderer.GENERATE_HEADER_ID, true)
			.set(HtmlRenderer.INDENT_SIZE, 2)
			.set(HtmlRenderer.PERCENT_ENCODE_URLS, true)
			.set(HtmlRenderer.ESCAPE_HTML, true)
		
			.set(TablesExtension.COLUMN_SPANS, true)
			.set(TablesExtension.APPEND_MISSING_COLUMNS, true)
			.set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
			.set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)	
			.set(TablesExtension.CLASS_NAME, "confluenceTable");
        
        Boolean linkifyHeaders = Boolean.parseBoolean(parameters.containsKey("LinkifyHeaders") ? parameters.get("LinkifyHeaders") : "true");
		List<Extension> extensions = new ArrayList<>();
		extensions.add(TablesExtension.create());
		extensions.add(StrikethroughSubscriptExtension.create());
		extensions.add(InsExtension.create());
		extensions.add(TaskListExtension.create());
		extensions.add(FootnoteExtension.create());
		extensions.add(WikiLinkExtension.create());
		extensions.add(DefinitionExtension.create());
		extensions.add(AutolinkExtension.create());
		extensions.add(SuperscriptExtension.create());
		extensions.add(YouTubeLinkExtension.create());
        extensions.add(TocExtension.create());

        if (linkifyHeaders){
            extensions.add(AnchorLinkExtension.create());
            options.set(HtmlRenderer.GENERATE_HEADER_ID, true);
        }

        Boolean isAzureDevOpsEnabled = MarkdownHelper.GetMarkdownConfig(bandanaManager, context)
                                                     .getIsAzureDevOpsEnabled();
        if (isAzureDevOpsEnabled){
            extensions.add(ResizableImageExtension.create());
        }

		options.set(Parser.EXTENSIONS, extensions);

        String highlightjs = "<script>\n" +
                "AJS.$('[data-macro-name=\"markdown\"] code').each(function(i, block) {\n" +
                "    hljs.highlightBlock(block);\n" +
                "  });\n" +
                "</script>";

        String highlightjscss = "<style>\n" +
                ".hljs {display: inline;}\n" +
                "pre > code {display: block !important;}\n" +
                "</style>";

        String rendermermaidjs ="<script>\n" +
                "AJS.$('[data-macro-name=\"markdown\"] .language-mermaid').each(function(i, block) {\n" +
                "const config = {\n"+
                "     securityLevel:'sandbox',\n"+
                "    startOnLoad:true,\n"+
                "    theme: 'default',\n"+
                "    flowchart:{\n"+
                "            useMaxWidth:false,\n"+
                "            htmlLabels:true\n"+
                "        }\n"+
                "};\n"+
                "mermaid.initialize(config);\n"+
                "mermaid.init(undefined, block);\n"+
                "  });\n" +
                "</script>";

        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();

        Node document = parser.parse(bodyContent);

        String htmlBody = renderer.render(document);

        Document doc = Jsoup.parseBodyFragment(htmlBody);
        Element body = doc.body();
        Elements th = body.getElementsByTag("th");
        Elements td = body.getElementsByTag("td");
        for (Element thi : th) {
			String alignment = thi.attributes().get("align");
			if (alignment != null && !alignment.isEmpty()) thi.attr("style", "text-align: " + alignment + ";");	
            thi.addClass("confluenceTh");
        }
        for (Element tdi : td) {
			String alignment = tdi.attributes().get("align");
			if (alignment != null && !alignment.isEmpty()) tdi.attr("style", "text-align: " + alignment + ";");
            tdi.addClass("confluenceTd");
        }
        
        PolicyFactory policy = new HtmlPolicyBuilder()
        		.allowCommonInlineFormattingElements()
        		.allowCommonBlockElements()
        		.allowStyling()
        		.allowStandardUrlProtocols()
        		.allowElements("a", "table", "tr", "td", "th", "thead", "tbody", "img", "hr", "input", "code", "pre", "dl", "dt", "dd")
        	    .allowAttributes("href", "title").onElements("a")
		        .allowAttributes("align", "class").onElements("table", "tr", "td", "th", "thead", "tbody")
        		.allowAttributes("id").onElements("h1", "h2", "h3", "h4", "h5", "h6", "sup", "li")
        	    .allowAttributes("alt", "src", "width", "height").onElements("img")
        	    .allowAttributes("class").onElements("li", "code")
        	    .allowAttributes("type", "class", "checked", "disabled", "readonly").onElements("input")
		        .allowTextIn("table")
        		.toFactory();
        String sanitizedBody = policy.sanitize(body.html());
        String html =  sanitizedBody +  highlightjs + rendermermaidjs + highlightjscss;

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
