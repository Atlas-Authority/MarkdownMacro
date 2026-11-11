package com.atlassian.plugins.confluence.markdown;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.content.render.xhtml.DefaultConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugin.spring.scanner.annotation.imports.ConfluenceImport;
import com.atlassian.plugins.confluence.markdown.ext.DevOpsResizableImage.ResizableImageExtension;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
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

import java.util.Map;
import java.util.ArrayList;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

import jakarta.inject.Inject;


public class MarkdownMacro extends BaseMacro implements Macro {

    private final PageBuilderService pageBuilderService;
    private final PluginSettingsFactory pluginSettingsFactory;

    @Inject
    public MarkdownMacro(@ConfluenceImport PageBuilderService pageBuilderService, @ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pageBuilderService = pageBuilderService;
        this.pluginSettingsFactory = pluginSettingsFactory;
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


    	// Highlighting and Mermaid rendering run from this web-resource (pulls in highlightjs and
    	// mermaid as dependencies); inline <script> in macro output is blocked by Confluence's CSP.
    	pageBuilderService.assembler().resources().requireWebResource(MarkdownHelper.MACRO_INIT_WEB_RESOURCE);

		final MutableDataSet options = new MutableDataSet()
			.set(HtmlRenderer.GENERATE_HEADER_ID, true)
			.set(HtmlRenderer.INDENT_SIZE, 2)
			.set(HtmlRenderer.PERCENT_ENCODE_URLS, true)
			.set(HtmlRenderer.ESCAPE_HTML, true)
		
			.set(TablesExtension.COLUMN_SPANS, true)
			.set(TablesExtension.APPEND_MISSING_COLUMNS, true)
			.set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
			.set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)	
			.set(TablesExtension.CLASS_NAME, "confluenceTable");
        
        final Boolean linkifyHeaders = Boolean.parseBoolean(parameters.containsKey("LinkifyHeaders") ? parameters.get("LinkifyHeaders") : "true");
		final var extensions = new ArrayList<Extension>();
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

        final Boolean isAzureDevOpsEnabled = MarkdownHelper.GetMarkdownConfig(pluginSettingsFactory)
                                                     .getIsAzureDevOpsEnabled();
        if (isAzureDevOpsEnabled){
            extensions.add(ResizableImageExtension.create());
        }

		options.set(Parser.EXTENSIONS, extensions);

        final var parser = Parser.builder(options).build();
        final var renderer = HtmlRenderer.builder(options).build();

        final Node document = parser.parse(bodyContent);

        final String htmlBody = renderer.render(document);

        final var doc = Jsoup.parseBodyFragment(htmlBody);
        final Element body = doc.body();
        final Elements th = body.getElementsByTag("th");
        final Elements td = body.getElementsByTag("td");
        for (final Element thi : th) {
			final String alignment = thi.attributes().get("align");
			if (alignment != null && !alignment.isEmpty()) thi.attr("style", "text-align: " + alignment + ";");	
            thi.addClass("confluenceTh");
        }
        for (final Element tdi : td) {
			final String alignment = tdi.attributes().get("align");
			if (alignment != null && !alignment.isEmpty()) tdi.attr("style", "text-align: " + alignment + ";");
            tdi.addClass("confluenceTd");
        }
        
        final PolicyFactory policy = new HtmlPolicyBuilder()
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
        return policy.sanitize(body.html());

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
