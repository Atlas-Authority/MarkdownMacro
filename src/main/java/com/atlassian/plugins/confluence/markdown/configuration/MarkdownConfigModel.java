package com.atlassian.plugins.confluence.markdown.configuration;

import java.util.ArrayList;
import java.util.List;

public class MarkdownConfigModel {
    private List<String> whitelist = new ArrayList<String>();
    private boolean enabled = false;

    public List<String> getWhitelist() {
        return whitelist;
    }

    public MarkdownConfigModel setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
        return this;
    }
    
    public boolean getEnabled() {
    	return enabled;
    }
    
    public MarkdownConfigModel setEnabled(boolean enabled) {
    	this.enabled = enabled;
    	return this;
    }

}