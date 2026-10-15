package dev.graphnous.enhancer;

import dev.graphnous.core.model.ConfigurationProperty;
import dev.graphnous.core.model.Module;
import dev.graphnous.core.model.ScanResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationTest {

    @Test
    void resolvesPlaceholdersWithThePropertiesOrTheirDefaults() {
        final var configuration = configuration(List.of(
            property("api.prefix", "/api/${api.version}", null),
            property("api.version", "v1", null)
        ));

        assertThat(configuration.resolve("${api.prefix}/orders")).isEqualTo("/api/v1/orders");
        assertThat(configuration.resolve("${orders.path:/orders}/{id}")).isEqualTo("/orders/{id}");
        assertThat(configuration.resolve("${orders.item:/{id}}")).isEqualTo("/{id}");
        assertThat(configuration.resolve("${orders.path:${api.prefix}/orders}")).isEqualTo("/api/v1/orders");
        assertThat(configuration.resolve("/orders")).isEqualTo("/orders");
        assertThat(configuration.get("api.prefix")).contains("/api/v1");
        assertThat(configuration.get("orders.path")).isEmpty();
    }

    @Test
    void leavesPlaceholdersOfKeysThatAreNotSetAsWritten() {
        final var configuration = configuration(List.of());

        assertThat(configuration.resolve("${API_PREFIX}/orders")).isEqualTo("${API_PREFIX}/orders");
        assertThat(configuration.resolve("${unclosed")).isEqualTo("${unclosed");
    }

    @Test
    void comparesKeysAsSpringBindsThem() {
        final var configuration = configuration(List.of(property("api.basePath", "/api", null)));

        assertThat(configuration.resolve("${api.base-path}")).isEqualTo("/api");
        assertThat(configuration.get("API.BASE_PATH")).contains("/api");
    }

    @Test
    void stopsAtACycle() {
        final var configuration = configuration(List.of(
            property("a", "${b}", null),
            property("b", "${a}", null)
        ));

        assertThat(configuration.resolve("${a}")).isEqualTo("${a}");
    }

    @Test
    void usesTheDefaultConfigurationOfTheModuleBeforeThatOfOthers() {
        final var library = module("library", List.of(property("orders.topic", "library", null)));
        final var app = module("app", List.of(
            property("orders.topic", "app", null),
            property("orders.group", "app", null),
            property("orders.group", "prod", "prod")
        ));

        final var result = new ScanResult();
        result.getModules().addAll(List.of(library, app));

        assertThat(Configuration.of(result, library).get("orders.topic")).contains("library");
        assertThat(Configuration.of(result, library).get("orders.group")).contains("app");
        assertThat(Configuration.of(result, app).get("orders.topic")).contains("app");
    }

    private static Configuration configuration(final List<ConfigurationProperty> properties) {
        final var module = module(".", properties);
        final var result = new ScanResult();
        result.getModules().add(module);

        return Configuration.of(result, module);
    }

    private static Module module(final String path, final List<ConfigurationProperty> properties) {
        final var module = new Module();
        module.setPath(path);
        module.setConfiguration(properties);

        return module;
    }

    private static ConfigurationProperty property(final String key, final String value, final String profile) {
        final var property = new ConfigurationProperty();
        property.setKey(key);
        property.setValue(value);
        property.setFile("src/main/resources/application.yml");
        property.setProfile(profile);

        return property;
    }
}
