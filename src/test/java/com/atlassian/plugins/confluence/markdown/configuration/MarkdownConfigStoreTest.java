package com.atlassian.plugins.confluence.markdown.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MarkdownConfigStoreTest {

    private static final String NEW_JSON = "{\"config\":{\"enabled\":true}}";
    private static final String LEGACY_JSON = "{\"config\":{\"enabled\":false}}";

    @Test
    void newNamespaceWins() {
        final var factory = new FakePluginSettingsFactory();
        factory.settings(MarkdownConfigStore.NAMESPACE).put(MarkdownConfigStore.CONFIG_KEY, NEW_JSON);
        factory.settings(MarkdownConfigStore.LEGACY_NAMESPACE).put(MarkdownConfigStore.CONFIG_KEY, LEGACY_JSON);

        assertEquals(NEW_JSON, MarkdownConfigStore.readConfigJson(factory));
        assertEquals(NEW_JSON, factory.settings(MarkdownConfigStore.NAMESPACE).get(MarkdownConfigStore.CONFIG_KEY));
    }

    @Test
    void legacyValueIsUsedAndMigratedForwardWhenNewIsEmpty() {
        final var factory = new FakePluginSettingsFactory();
        factory.settings(MarkdownConfigStore.LEGACY_NAMESPACE).put(MarkdownConfigStore.CONFIG_KEY, LEGACY_JSON);

        assertEquals(LEGACY_JSON, MarkdownConfigStore.readConfigJson(factory));
        assertEquals(LEGACY_JSON, factory.settings(MarkdownConfigStore.NAMESPACE).get(MarkdownConfigStore.CONFIG_KEY));
    }

    @Test
    void blankOrNullStringsAreTreatedAsAbsent() {
        for (final var absent : new String[] {"", "   ", "null", "NULL"}) {
            final var factory = new FakePluginSettingsFactory();
            factory.settings(MarkdownConfigStore.NAMESPACE).put(MarkdownConfigStore.CONFIG_KEY, absent);
            factory.settings(MarkdownConfigStore.LEGACY_NAMESPACE).put(MarkdownConfigStore.CONFIG_KEY, LEGACY_JSON);
            assertEquals(LEGACY_JSON, MarkdownConfigStore.readConfigJson(factory), "new value: '" + absent + "'");

            final var legacyOnly = new FakePluginSettingsFactory();
            legacyOnly.settings(MarkdownConfigStore.LEGACY_NAMESPACE).put(MarkdownConfigStore.CONFIG_KEY, absent);
            assertNull(MarkdownConfigStore.readConfigJson(legacyOnly), "legacy value: '" + absent + "'");
            assertNull(legacyOnly.settings(MarkdownConfigStore.NAMESPACE).get(MarkdownConfigStore.CONFIG_KEY));
        }
    }

    @Test
    void bothAbsentReturnsNull() {
        final var factory = new FakePluginSettingsFactory();

        assertNull(MarkdownConfigStore.readConfigJson(factory));
        assertNull(factory.settings(MarkdownConfigStore.NAMESPACE).get(MarkdownConfigStore.CONFIG_KEY));
    }

    @Test
    void writeStoresIntoNewNamespace() {
        final var factory = new FakePluginSettingsFactory();

        MarkdownConfigStore.writeConfigJson(factory, NEW_JSON);

        assertEquals(NEW_JSON, factory.settings(MarkdownConfigStore.NAMESPACE).get(MarkdownConfigStore.CONFIG_KEY));
        assertNull(factory.settings(MarkdownConfigStore.LEGACY_NAMESPACE).get(MarkdownConfigStore.CONFIG_KEY));
        assertEquals(NEW_JSON, MarkdownConfigStore.readConfigJson(factory));
    }

    private static final class FakePluginSettingsFactory implements PluginSettingsFactory {
        private final Map<String, FakePluginSettings> byNamespace = new HashMap<>();

        FakePluginSettings settings(String namespace) {
            return byNamespace.computeIfAbsent(namespace, k -> new FakePluginSettings());
        }

        @Override
        public PluginSettings createSettingsForKey(String key) {
            return settings(key);
        }

        @Override
        public PluginSettings createGlobalSettings() {
            return settings(null);
        }
    }

    private static final class FakePluginSettings implements PluginSettings {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public Object get(String key) {
            return values.get(key);
        }

        @Override
        public Object put(String key, Object value) {
            return values.put(key, value);
        }

        @Override
        public Object remove(String key) {
            return values.remove(key);
        }
    }
}
