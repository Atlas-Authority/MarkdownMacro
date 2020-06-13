package com.atlassian.plugins.confluence.markdown.configuration;


public class MacroConfigModel {
    private MarkdownConfigModel config = new MarkdownConfigModel();
    private boolean changed = false;

    public MarkdownConfigModel getConfig() {
        return config;
    }

    public MacroConfigModel setConfig(MarkdownConfigModel config) {
        this.config = config;
        return this;
    }
    
    public boolean isChanged() {
    	return this.changed;
    }
    
    public MacroConfigModel setIsChanged(boolean changed) {
        this.changed = changed;
        return this;
    }
}
