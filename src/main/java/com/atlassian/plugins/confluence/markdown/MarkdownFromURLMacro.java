package com.atlassian.plugins.confluence.markdown;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.content.render.xhtml.DefaultConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;

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

import com.atlassian.plugins.confluence.markdown.configuration.MacroConfigModel;
import static com.atlassian.plugins.confluence.markdown.configuration.PluginAdminGetConfigurationAction.PLUGIN_CONFIG_KEY;
import com.atlassian.plugins.confluence.markdown.utils.IPAddressUtil;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import java.nio.charset.StandardCharsets;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

public class MarkdownFromURLMacro extends BaseMacro implements Macro {

    private final XhtmlContent xhtmlUtils;
    private BandanaManager bandanaManager;
    private ConfluenceBandanaContext context = new ConfluenceBandanaContext("markdown-plugin");
    private ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private List<String[]> whitelistDomains;
    private List<InetAddress> whitelistIPs;
    private boolean enabled;

    private PageBuilderService pageBuilderService;
    
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

    @Autowired
    public MarkdownFromURLMacro(@ComponentImport PageBuilderService pageBuilderService, XhtmlContent xhtmlUtils, BandanaManager bandanaManager) {
        this.pageBuilderService = pageBuilderService;
        this.xhtmlUtils = xhtmlUtils;
        this.bandanaManager = bandanaManager;
    }
    
    private void initWhitelistConfiguration() throws UnknownHostException {
        MacroConfigModel model = new MacroConfigModel();
        String config = (String) this.bandanaManager.getValue(context, PLUGIN_CONFIG_KEY);
        if (config != null) {
            try {
                model = objectMapper.readValue(config, MacroConfigModel.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        enabled = model.getConfig().getEnabled();
        whitelistDomains = new ArrayList<String[]>();
        whitelistIPs = new ArrayList<InetAddress>();
        for (String domain : model.getConfig().getWhitelist()) {
        	if (domain.length() == 0) continue;
        	
        	if (isIPLiteral(domain)) {
        		whitelistIPs.add(InetAddress.getByName(domain));
        	} else {
	        	String[] domainComponents = domain.split("\\.");
	        	whitelistDomains.add(domainComponents);
        	}
        }
    }
    
    private boolean isIPLiteral(String domain) {
    	return (IPAddressUtil.textToNumericFormatV4(domain) != null) || (IPAddressUtil.textToNumericFormatV6(domain) != null);
    }
    
    private boolean isAllowedToProceed(URL url) throws UnknownHostException {
        if (!enabled) return true;
        
        String host = url.getHost();
        
        String[] hostComponents = host.split("\\.");
        for (String[] whitelistDomainComponents : whitelistDomains) {
            if (hostComponents.length < whitelistDomainComponents.length) continue;
            boolean isMatch = true;
        	for (int i = 0; i < whitelistDomainComponents.length; i++) {
        		if (!hostComponents[hostComponents.length - i - 1].equalsIgnoreCase(whitelistDomainComponents[whitelistDomainComponents.length - i - 1])) isMatch = false;
        	}
        	if (isMatch) return true;
        }
        
        InetAddress inetAddr = InetAddress.getByName(host);
        for (InetAddress whitelistIP : whitelistIPs) {
        	if (inetAddr.equals(whitelistIP)) return true;
        }
        
        return false;
    }

    private URL getFinalURL(URL url) throws IOException, IllegalRedirectException {
    	HttpURLConnection con = (HttpURLConnection) url.openConnection();
    	con.setInstanceFollowRedirects(false);
    	con.connect();
    	con.getInputStream();
    	
    	int responseCode = con.getResponseCode();
    	if (responseCode >= 300 && responseCode < 400) {
    		String redirectUrlString = con.getHeaderField("Location");
    		if (redirectUrlString == null) throw new IllegalRedirectException();
			try {
				URL redirectUrl = new URL(url, redirectUrlString);
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

			pageBuilderService.assembler().resources().requireWebResource("com.atlassian.plugins.confluence.markdown.confluence-markdown-macro:highlightjs");

			MutableDataSet options = new MutableDataSet()
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

			String highlightjs = "<script>\n" +
					"AJS.$('[data-macro-name=\"markdown-from-url\"] code').each(function(i, block) {\n" +
					"    hljs.highlightBlock(block);\n" +
					"  });\n" +
					"</script>";

			String highlightjscss = "<style>\n"+
					".hljs {display: inline;}\n" +
					"pre > code {display: block !important;}\n" +
					"</style>";

			URL importFrom = null;
			try {
				importFrom = new URL(bodyContent);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}

			Boolean useRelativePathsAzureDevOps = Boolean.parseBoolean(parameters.containsKey("UseAzureDevOpsRelativePathUrls") ? parameters.get("UseAzureDevOpsRelativePathUrls") : "false");

			List<Extension> extensions = new ArrayList<>();
			extensions.add(TablesExtension.create());
			extensions.add(StrikethroughSubscriptExtension.create());
			extensions.add(StrikethroughSubscriptExtension.create());
			extensions.add(InsExtension.create());
			extensions.add(TaskListExtension.create());
			extensions.add(FootnoteExtension.create());
			extensions.add(WikiLinkExtension.create());
			extensions.add(DefinitionExtension.create());
			extensions.add(AnchorLinkExtension.create());
			extensions.add(SuperscriptExtension.create());
			extensions.add(YouTubeLinkExtension.create());
			extensions.add(TocExtension.create());

			if (useRelativePathsAzureDevOps) {
				pathToRepository = parameters.get("LinkAzureDevOpsRepository");
				Path repoPath = MarkdownRelativePathsDevOpsHelper.getPathFromRawMarkdownUrl(importFrom);

				MarkdownRelativeDevOpsUrls.relativePathDataKey = repoPath.toString();
				MarkdownRelativeDevOpsUrls.devOpsUrlDataKey = pathToRepository;

				extensions.add(MarkdownRelativeDevOpsUrls.CustomExtension.create());
			}
			else
			{
				extensions.add(AutolinkExtension.create());
			}

			options.set(Parser.EXTENSIONS, extensions);

			Parser parser = Parser.builder(options).build();
			HtmlRenderer renderer = HtmlRenderer.builder(options).build();
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
					InetAddress inetAddress = InetAddress.getByName(importFrom.getHost());
					if(inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
						throw new MalformedURLException();
					}
				}
					
				BufferedReader in = new BufferedReader(
					new InputStreamReader(importFrom.openStream(), StandardCharsets.UTF_8)
				);
				
				String inputLine;
				while ((inputLine = in.readLine()) != null) {
					toParse = toParse + "\n" + inputLine;
				}
				in.close();
				toParse = toParse.trim();
				if (toParse.startsWith("<html>\n<head>\n  <title>OpenID transaction in progress</title>")) {
					throw new PrivateRepositoryException("Cannot import from private repository.");
				} else {
					Node document = parser.parse(toParse);
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

					Elements links = body.getElementsByTag("a");
					Elements images = body.getElementsByTag("img");
					for (Element link : links) {
						String href = link.attributes().get("href");
						if (href != null && !href.isEmpty() && href.charAt(0) != '#') {
							link.attr("href", new URL(importFrom, href).toString());
						}	
					}
					for (Element image : images) {
						String src = image.attributes().get("src");
						if (src != null && !src.isEmpty() && src.charAt(0) != '#') {
							image.attr("src", new URL(importFrom, src).toString());
						}
					}
					
			        PolicyFactory policy = new HtmlPolicyBuilder()
			        		.allowCommonInlineFormattingElements()
			        		.allowCommonBlockElements()
			        		.allowStyling()
			        		.allowStandardUrlProtocols()
			        		.allowElements("a", "table", "tr", "td", "th", "thead", "tbody", "img", "hr", "input", "code", "pre", "dl", "dt", "dd")
			        	    .allowAttributes("href").onElements("a")
					        .allowAttributes("align", "class").onElements("table", "tr", "td", "th", "thead", "tbody")
			        		.allowAttributes("id").onElements("h1", "h2", "h3", "h4", "h5", "h6", "sup", "li")
			        	    .allowAttributes("alt", "src").onElements("img")
			        	    .allowAttributes("class").onElements("li", "code")
			        	    .allowAttributes("type", "class", "checked", "disabled", "readonly").onElements("input")
					        .allowTextIn("table")
			        		.toFactory();
			        String sanitizedBody = policy.sanitize(body.html());
					if (useRelativePathsAzureDevOps) {
						sanitizedBody = sanitizedBody.replace("?path&#61;", "?path=");
					}
			        html =  sanitizedBody +  highlightjs + highlightjscss;

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
