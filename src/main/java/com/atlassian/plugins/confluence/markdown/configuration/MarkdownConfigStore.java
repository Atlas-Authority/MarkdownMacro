package com.atlassian.plugins.confluence.markdown.configuration;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

/**
 * Single access point for the plugin configuration JSON stored in SAL PluginSettings.
 *
 * Older versions stored the config in Bandana context "markdown-plugin". Confluence 10 copied
 * every Bandana context into plugin settings namespaced by the context key, so on Confluence 11
 * (where Bandana is removed) the legacy value is read from LEGACY_NAMESPACE and migrated forward.
 */
public final class MarkdownConfigStore {
    public static final String NAMESPACE = "com.atlassian.plugins.confluence.markdown";
    public static final String LEGACY_NAMESPACE = "markdown-plugin";
    public static final String CONFIG_KEY = "markdown-plugin-config-00";

    private MarkdownConfigStore() {
    }

    /**
     * Reads config from NAMESPACE. If not present, reads from LEGACY_NAMESPACE and migrates it
     * to NAMESPACE (so future reads are from the new store).
     */
    public static String readConfigJson(PluginSettingsFactory pluginSettingsFactory) {
        // 1) Try the current namespace first
        PluginSettings ps = pluginSettingsFactory.createSettingsForNamespace(NAMESPACE);
        Object v = ps.get(CONFIG_KEY);
        if (isUsable(v)) {
            return (String) v;
        }

        // 2) Fallback to the legacy (former Bandana) namespace and migrate if found
        Object legacy = pluginSettingsFactory.createSettingsForNamespace(LEGACY_NAMESPACE).get(CONFIG_KEY);
        if (isUsable(legacy)) {
            String legacyJson = ((String) legacy).trim();
            ps.put(CONFIG_KEY, legacyJson);
            return legacyJson;
        }

        return null;
    }

    public static void writeConfigJson(PluginSettingsFactory pluginSettingsFactory, String json) {
        pluginSettingsFactory.createSettingsForNamespace(NAMESPACE).put(CONFIG_KEY, json);
    }

    private static boolean isUsable(Object v) {
        return v instanceof String && !((String) v).trim().isEmpty() && !"null".equalsIgnoreCase(((String) v).trim());
    }
}
