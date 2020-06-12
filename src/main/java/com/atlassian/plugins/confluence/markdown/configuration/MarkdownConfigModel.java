package com.atlassian.plugins.confluence.markdown.configuration;

import java.util.ArrayList;
import java.util.List;

public class MarkdownConfigModel {
    private List<String> bwList = new ArrayList<String>();
    private boolean enabled = false;

    public List<String> getBwList() {
        return bwList;
    }

    public MarkdownConfigModel setBwList(List<String> bwList) {
        this.bwList = bwList;
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