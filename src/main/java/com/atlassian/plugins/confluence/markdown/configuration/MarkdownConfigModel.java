package com.atlassian.plugins.confluence.markdown.configuration;

import java.util.ArrayList;
import java.util.List;

public class MarkdownConfigModel {
    private List<String> whitelist = new ArrayList<>();
    private boolean enabled = false;
    private boolean isAzureDevOpsEnabled = false;

    public boolean getIsAzureDevOpsEnabled(){
        return isAzureDevOpsEnabled;
    }

    public void setIsAzureDevOpsEnabled(boolean isAzureDevOpsEnabled){
        this.isAzureDevOpsEnabled = isAzureDevOpsEnabled;
    }

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
    
    @Override
    public boolean equals(Object o) {
    	if (o == this) return true;
    	if (!(o instanceof MarkdownConfigModel that)) return false;
    	
    	if (that.getEnabled() != this.enabled) return false;
    	if (!that.getWhitelist().equals(this.whitelist)) return false;
        if (that.getIsAzureDevOpsEnabled() != this.isAzureDevOpsEnabled) return false;
    	
    	return true;
    }
}