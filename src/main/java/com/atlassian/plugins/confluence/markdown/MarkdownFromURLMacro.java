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
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.superscript.SuperscriptExtension;
import com.vladsch.flexmark.util.options.MutableDataSet;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;


public class MarkdownFromURLMacro extends BaseMacro implements Macro {

    private final XhtmlContent xhtmlUtils;

    private PageBuilderService pageBuilderService;

    @Autowired
    public MarkdownFromURLMacro(@ComponentImport PageBuilderService pageBuilderService, XhtmlContent xhtmlUtils) {
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

    public String execute(Map<String, String> parameters, String bodyContent, ConversionContext conversionContext) throws MacroExecutionException
    {

		if (bodyContent != null) {
			pageBuilderService.assembler().resources().requireWebResource("com.atlassian.plugins.confluence.markdown.confluence-markdown-macro:highlightjs");

			MutableDataSet options = new MutableDataSet()
                    .set(HtmlRenderer.GENERATE_HEADER_ID, true)
                    .set(HtmlRenderer.INDENT_SIZE, 2)
                    .set(HtmlRenderer.PERCENT_ENCODE_URLS, true)
                    // for full GFM table compatibility add the following table extension options:

                    .set(TablesExtension.COLUMN_SPANS, false)
                    .set(TablesExtension.APPEND_MISSING_COLUMNS, true)
                    .set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
                    .set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)
                    .set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()))
                    ;

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
				YouTubeLinkExtension.create()

			));


			String highlightjs = "<script>\n" +
					"AJS.$('[data-macro-name=\"markdown\"] code').each(function(i, block) {\n" +
					"    hljs.highlightBlock(block);\n" +
					"  });\n" +
					"</script>";

			class privateRepositoryException extends Exception {
				public privateRepositoryException(String message) {
					super(message);
				}
			}
			
			Parser parser = Parser.builder(options).build();
			HtmlRenderer renderer = HtmlRenderer.builder(options).build();
			
			String exceptionsToReturn = "";
			String html = "";
			String toParse = "";
			try {
				URL importFrom = new URL(bodyContent);
				BufferedReader in = new BufferedReader(
					new InputStreamReader(importFrom.openStream())
				);
				String inputLine;
				while ((inputLine = in.readLine()) != null) {
					toParse = toParse + "\n" + inputLine;
				}
				in.close();
				toParse = toParse.trim();
				if (toParse.startsWith("<html>\n<head>\n  <title>OpenID transaction in progress</title>")) {
					throw new privateRepositoryException("Cannot import from private repository.");
				}else {
					Node document = parser.parse(toParse);
					html = renderer.render(document) + highlightjs;
				}
			}
			catch (MalformedURLException u) {
				exceptionsToReturn = exceptionsToReturn + "<strong>Error with Markdown From URL macro: Invalid URL.</strong><br>Please enter a valid URL. If you are not trying to import markdown from a URL, use the Markdown macro instead of the Markdown from URL macro.<br>For support <a href='https://community.atlassian.com/t5/tag/addon-com.atlassian.plugins.confluence.markdown.confluence-markdown-macro/tg-p'>visit our Q&A in the Atlassian Community</a>. You can ask a new question by clicking the \"Create\" button on the top right of the Q&A.<br>";
			}
			catch (privateRepositoryException p) {
				exceptionsToReturn = exceptionsToReturn + "<strong>Error with Markdown From URL macro: Importing from private Bitbucket repositories is not supported.</strong><br>Please make your repository public before importing. Alternatively, you can copy and paste your markdown into the Markdown macro.<br>If you are allowed access, you can find the markdown file <a href='" + bodyContent + "'>here</a>.<br>For support <a href='https://community.atlassian.com/t5/tag/addon-com.atlassian.plugins.confluence.markdown.confluence-markdown-macro/tg-p'>visit our Q&A in the Atlassian Community</a>. You can ask a new question by clicking the \"Create\" button on the top right of the Q&A.<br>";
			}
			catch (FileNotFoundException f) {
				exceptionsToReturn = exceptionsToReturn + "<strong>Error with Markdown From URL macro: URL does not exist.</strong><br>" + bodyContent + "<br>Please double check your URL. Perhaps you made a typo or perhaps the page has been moved.<br>This can also be caused by changing the Github repository containing the file from public to private. If this is the case go back to the raw file and re-copy the link.<br>For support <a href='https://community.atlassian.com/t5/tag/addon-com.atlassian.plugins.confluence.markdown.confluence-markdown-macro/tg-p'>visit our Q&A in the Atlassian Community</a>. You can ask a new question by clicking the \"Create\" button on the top right of the Q&A.<br>";
			}
			catch (IOException e) {
				exceptionsToReturn = exceptionsToReturn + "<strong>Error with Markdown From URL macro: Unexpected error.</strong><br>" + e.toString() + "<br>For support <a href='https://community.atlassian.com/t5/tag/addon-com.atlassian.plugins.confluence.markdown.confluence-markdown-macro/tg-p'>visit our Q&A in the Atlassian Community</a>. You can ask a new question by clicking the \"Create\" button on the top right of the Q&A.<br>"; 
			}
			finally {
				if (exceptionsToReturn != "") {
					html = "<p style='background: #ffe0e0; border-radius: 5px; padding: 10px;'>" + exceptionsToReturn + "</p>";
				}
				return html;
			}
 		}else {
			return "";
		}
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
