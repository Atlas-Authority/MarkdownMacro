package com.atlassian.plugins.confluence.markdown.configuration;


public class MacroConfigModel {
    private MarkdownConfigModel config = new MarkdownConfigModel();

    public MarkdownConfigModel getConfig() {
        return config;
    }

    public MacroConfigModel setConfig(MarkdownConfigModel config) {
        this.config = config;
        return this;
    }
}
