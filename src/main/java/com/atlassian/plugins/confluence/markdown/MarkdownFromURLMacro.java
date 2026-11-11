package com.atlassian.plugins.confluence.markdown;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.content.render.xhtml.DefaultConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugin.spring.scanner.annotation.imports.ConfluenceImport;
import com.atlassian.plugins.confluence.markdown.configuration.MacroConfigModel;
import com.atlassian.plugins.confluence.markdown.configuration.MarkdownConfigStore;
import com.atlassian.plugins.confluence.markdown.ext.DevOpsResizableImage.ResizableImageExtension;
import com.atlassian.plugins.confluence.markdown.utils.IPAddressUtil;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.definition.DefinitionExtension;
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughSubscriptExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.ins.InsExtension;
import com.vladsch.flexmark.ext.superscript.SuperscriptExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.toc.TocExtension;
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension;
import com.vladsch.flexmark.ext.youtube.embedded.YouTubeLinkExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.misc.Extension;
import jakarta.inject.Inject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MarkdownFromURLMacro extends BaseMacro implements Macro {

    private final PluginSettingsFactory pluginSettingsFactory;
    // Jackson 3: immutable mapper; keep Jackson 2 defaults so persisted config JSON reads as before
    private final ObjectMapper objectMapper = JsonMapper.builder(JsonFactory.builderWithJackson2Defaults().build())
            .configureForJackson2()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private List<String[]> whitelistDomains;
    private List<InetAddress> whitelistIPs;
	private boolean isAzureDevOpsEnabled;
    private boolean enabled;

    private final PageBuilderService pageBuilderService;
    
    class PrivateRepositoryException extends Exception {
		public PrivateRepositoryException(String message) {
			super(message);
		}
	}
	
	class NonWhitelistURLException extends Exception {
		public NonWhitelistURLException() {
			super();
		}
	}
	
	class IllegalRedirectException extends Exception {
		boolean isMalformed;
		
		public IllegalRedirectException() {
			super();
		}
		
		public IllegalRedirectException(boolean isMalformed, String message) {
			super(message);
			this.isMalformed = isMalformed;
		}
	}

	@Inject
    public MarkdownFromURLMacro(@ConfluenceImport PageBuilderService pageBuilderService, @ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pageBuilderService = pageBuilderService;
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    private void initWhitelistConfiguration() throws UnknownHostException {
        var model = new MacroConfigModel();
        final String config = MarkdownConfigStore.readConfigJson(this.pluginSettingsFactory);
        if (config != null) {
            try {
                model = objectMapper.readValue(config, MacroConfigModel.class);
            } catch (JacksonException e) {
                throw new RuntimeException(e);
            }
        }
        enabled = model.getConfig().getEnabled();
        whitelistDomains = new ArrayList<>();
        whitelistIPs = new ArrayList<>();
        for (final String domain : model.getConfig().getWhitelist()) {
        	if (domain.isEmpty()) continue;
        	
        	if (isIPLiteral(domain)) {
        		whitelistIPs.add(InetAddress.getByName(domain));
        	} else {
	        	final String[] domainComponents = domain.split("\\.");
	        	whitelistDomains.add(domainComponents);
        	}
        }
    }
    
    private boolean isIPLiteral(String domain) {
    	return (IPAddressUtil.textToNumericFormatV4(domain) != null) || (IPAddressUtil.textToNumericFormatV6(domain) != null);
    }
    
    private boolean isAllowedToProceed(URL url) throws UnknownHostException {
        if (!enabled) return true;
        
        final String host = url.getHost();
        
        final String[] hostComponents = host.split("\\.");
        for (final String[] whitelistDomainComponents : whitelistDomains) {
            if (hostComponents.length < whitelistDomainComponents.length) continue;
            boolean isMatch = true;
        	for (int i = 0; i < whitelistDomainComponents.length; i++) {
        		if (!hostComponents[hostComponents.length - i - 1].equalsIgnoreCase(whitelistDomainComponents[whitelistDomainComponents.length - i - 1])) isMatch = false;
        	}
        	if (isMatch) return true;
        }
        
        final InetAddress inetAddr = InetAddress.getByName(host);
        for (final InetAddress whitelistIP : whitelistIPs) {
        	if (inetAddr.equals(whitelistIP)) return true;
        }
        
        return false;
    }

    private URL getFinalURL(URL url) throws IOException, IllegalRedirectException {
    	final var con = (HttpURLConnection) url.openConnection();
    	con.setInstanceFollowRedirects(false);
    	con.connect();
    	con.getInputStream();
    	
    	final int responseCode = con.getResponseCode();
    	if (responseCode >= 300 && responseCode < 400) {
    		final String redirectUrlString = con.getHeaderField("Location");
    		if (redirectUrlString == null) throw new IllegalRedirectException();
			try {
				final var redirectUrl = new URL(url, redirectUrlString);
				if (!isAllowedToProceed(redirectUrl)) {
	        		throw new IllegalRedirectException(false, redirectUrlString);
	        	}
				return getFinalURL(redirectUrl);
			} catch (MalformedURLException e) {
				throw new IllegalRedirectException(true, redirectUrlString);
			}
    	}
    	
    	return url;
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

			// Highlighting and Mermaid rendering run from this web-resource (pulls in highlightjs and
			// mermaid as dependencies); inline <script> in macro output is blocked by Confluence's CSP.
			pageBuilderService.assembler().resources().requireWebResource(MarkdownHelper.MACRO_INIT_WEB_RESOURCE);

			final MutableDataSet options = new MutableDataSet()
				.set(HtmlRenderer.GENERATE_HEADER_ID, true)
				.set(HtmlRenderer.INDENT_SIZE, 2)
				.set(HtmlRenderer.PERCENT_ENCODE_URLS, true)
				.set(HtmlRenderer.ESCAPE_HTML, true)

				// for full GFM table compatibility add the following table extension options:
				.set(TablesExtension.COLUMN_SPANS, false)
				.set(TablesExtension.APPEND_MISSING_COLUMNS, true)
				.set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
				.set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)
				.set(TablesExtension.CLASS_NAME, "confluenceTable");

			String pathToRepository = "";
			String exceptionsToReturn = "";
			String html = "";
			String toParse = "";

			URL importFrom = null;
			try {
				importFrom = new URL(bodyContent);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}

			isAzureDevOpsEnabled = MarkdownHelper.GetMarkdownConfig(pluginSettingsFactory)
												 .getIsAzureDevOpsEnabled();

			final Boolean linkifyHeaders = Boolean.parseBoolean(parameters.containsKey("LinkifyHeaders") ? parameters.get("LinkifyHeaders") : "true");
			final Boolean useRelativePathsAzureDevOps = isAzureDevOpsEnabled && parameters.containsKey("LinkAzureDevOpsRepository")
					? !MarkdownHelper.isNullOrEmpty(parameters.get("LinkAzureDevOpsRepository"))
					: false;

			final var extensions = new ArrayList<Extension>();
			extensions.add(TablesExtension.create());
			extensions.add(StrikethroughSubscriptExtension.create());
			extensions.add(InsExtension.create());
			extensions.add(TaskListExtension.create());
			extensions.add(FootnoteExtension.create());
			extensions.add(WikiLinkExtension.create());
			extensions.add(DefinitionExtension.create());
			extensions.add(SuperscriptExtension.create());
			extensions.add(YouTubeLinkExtension.create());
			extensions.add(TocExtension.create());
		
			if (linkifyHeaders){
				extensions.add(AnchorLinkExtension.create());
				options.set(HtmlRenderer.GENERATE_HEADER_ID, true);
			}

			if (isAzureDevOpsEnabled){
				extensions.add(ResizableImageExtension.create());
			}

			if (useRelativePathsAzureDevOps) {
				pathToRepository = parameters.get("LinkAzureDevOpsRepository");
				final Path repoPath = MarkdownHelper.getPathFromRawMarkdownUrl(importFrom);

				MarkdownRelativeDevOpsUrls.relativePathDataKey = repoPath.toString();
				MarkdownRelativeDevOpsUrls.devOpsUrlDataKey = pathToRepository;
				MarkdownRelativeDevOpsUrls.importFrom = importFrom.toString();

				extensions.add(MarkdownRelativeDevOpsUrls.CustomExtension.create());
			}
			else
			{
				extensions.add(AutolinkExtension.create());
			}

			options.set(Parser.EXTENSIONS, extensions);

			final var parser = Parser.builder(options).build();
			final var renderer = HtmlRenderer.builder(options).build();
			try {
				initWhitelistConfiguration();

				if (importFrom == null){
					throw new Exception("import Url is invalid");
				}
				
				if(!importFrom.getProtocol().startsWith("http")) {
					throw new MalformedURLException();
				}
				
				if (enabled) {	
					if (!isAllowedToProceed(importFrom)) {
						throw new NonWhitelistURLException();
					}
					importFrom = getFinalURL(importFrom);
				} else {
					final var inetAddress = InetAddress.getByName(importFrom.getHost());
					if(inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
						throw new MalformedURLException();
					}
				}
					
				try (var in = new BufferedReader(
					new InputStreamReader(importFrom.openStream(), StandardCharsets.UTF_8)
				)) {
					String inputLine;
					while ((inputLine = in.readLine()) != null) {
						toParse = toParse + "\n" + inputLine;
					}
				}
				toParse = toParse.trim();
				if (toParse.startsWith("<html>\n<head>\n  <title>OpenID transaction in progress</title>")) {
					throw new PrivateRepositoryException("Cannot import from private repository.");
				} else {
					final Node document = parser.parse(toParse);
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

					final Elements links = body.getElementsByTag("a");
					final Elements images = body.getElementsByTag("img");
					for (final Element link : links) {
						final String href = link.attributes().get("href");
						if (href != null && !href.isEmpty() && href.charAt(0) != '#') {
							link.attr("href", new URL(importFrom, href).toString());
						}	
					}
					for (final Element image : images) {
						final String src = image.attributes().get("src");
						if (src != null && !src.isEmpty() && src.charAt(0) != '#') {
							image.attr("src", new URL(importFrom, src).toString());
						}
					}
					
			        final PolicyFactory policy = new HtmlPolicyBuilder()
			        		.allowCommonInlineFormattingElements()
			        		.allowCommonBlockElements()
			        		.allowStyling()
			        		.allowStandardUrlProtocols()
			        		.allowElements("a", "table", "tr", "td", "th", "thead", "tbody", "img", "hr", "input", "code", "pre", "dl", "dt", "dd")
			        	    .allowAttributes("href").onElements("a")
					        .allowAttributes("align", "class").onElements("table", "tr", "td", "th", "thead", "tbody")
			        		.allowAttributes("id").onElements("h1", "h2", "h3", "h4", "h5", "h6", "sup", "li")
			        	    .allowAttributes("alt", "src", "width", "height").onElements("img")
			        	    .allowAttributes("class").onElements("li", "code")
			        	    .allowAttributes("type", "class", "checked", "disabled", "readonly").onElements("input")
					        .allowTextIn("table")
			        		.toFactory();
			        String sanitizedBody = policy.sanitize(body.html());
					if (useRelativePathsAzureDevOps) {
						sanitizedBody = sanitizedBody.replace("?path&#61;", "?path=");
					}
			        html = sanitizedBody;

					return html;
				}
			}
			catch (MalformedURLException u) {
				exceptionsToReturn += "<strong>Error with Markdown From URL macro: Invalid URL.</strong><br>Please enter a valid URL."
						+ " If you are not trying to import markdown from a URL, use the Markdown macro instead of the Markdown from "
						+ "URL macro.<br>For support <a href='https://community.atlassian.com/t5/tag/addon-com.atlassian.plugins."
						+ "confluence.markdown.confluence-markdown-macro/tg-p'>visit our Q&A in the Atlassian Community</a>. You can "
						+ "ask a new question by clicking the \"Create\" button on the top right of the Q&A.<br>";
			}
			catch (PrivateRepositoryException p) {
				exceptionsToReturn = exceptionsToReturn + "<strong>Error with Markdown From URL macro: Importing from private Bitbucket "
						+ "repositories is not supported.</strong><br>Please make your repository public before importing. Alternatively, "
						+ "you can copy and paste your markdown into the Markdown macro.<br>If you are allowed access, you can find the "
						+ "markdown file <a href='" + bodyContent + "'>here</a>.<br>For support <a href='https://community.atlassian.com/"
						+ "t5/tag/addon-com.atlassian.plugins.confluence.markdown.confluence-markdown-macro/tg-p'>visit our Q&A in"
						+ " the Atlassian Community</a>. You can ask a new question by clicking the \"Create\" button on the top "
						+ "right of the Q&A.<br>";
			}
			catch (FileNotFoundException f) {
				exceptionsToReturn = exceptionsToReturn + "<strong>Error with Markdown From URL macro: URL does not exist.</strong><br>"
						+ bodyContent + "<br>Please double check your URL. Perhaps you made a typo or perhaps the page has been moved.<br>"
						+ "This can also be caused by changing the Github repository containing the file from public to private. If this is "
						+ "the case go back to the raw file and re-copy the link.<br>For support <a href='https://community.atlassian.com/t5"
						+ "/tag/addon-com.atlassian.plugins.confluence.markdown.confluence-markdown-macro/tg-p'>visit our Q&A in the Atlassian "
						+ "Community</a>. You can ask a new question by clicking the \"Create\" button on the top right of the Q&A.<br>";
			}
			catch (IOException e) {
				exceptionsToReturn = exceptionsToReturn + "<strong>Error with Markdown From URL macro: Unexpected error.</strong><br>" 
						+ e.toString() + "<br>For support <a href='https://community.atlassian.com/t5/tag/addon-com.atlassian.plugins."
						+ "confluence.markdown.confluence-markdown-macro/tg-p'>visit our Q&A in the Atlassian Community</a>. You can ask"
						+ " a new question by clicking the \"Create\" button on the top right of the Q&A.<br>"; 
			}
			catch (NonWhitelistURLException n) {
				exceptionsToReturn = exceptionsToReturn + "<strong>Error with Markdown From URL macro: </strong>URL has not been whitelisted by "
						+ "administrators.<br>";
			}
			catch (IllegalRedirectException r) {
				if (r.getMessage() != null) {
					if (r.isMalformed) exceptionsToReturn = exceptionsToReturn + "<strong>Error with Markdown From URL macro: </strong>Malformed"
							+ " redirect URL: " + r.getMessage() + ".<br>";
					else exceptionsToReturn = exceptionsToReturn + "<strong>Error with Markdown From URL macro: </strong>The following redirect"
							+ " URL has not been whitelisted by administrators: " + r.getMessage() + ".<br>";
				} else {
					exceptionsToReturn = exceptionsToReturn + "<strong>Error with Markdown From URL macro: </strong>NULL redirect.<br>";
				}
			}
			finally {
				if (exceptionsToReturn != "") {
					html = "<div class=\"aui-message aui-message-error\"><p class=\"title\"><strong>Error</strong></p><p>" + exceptionsToReturn + "</p></div>";
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
