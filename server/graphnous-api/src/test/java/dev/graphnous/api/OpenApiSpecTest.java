package dev.graphnous.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiSpecTest {

    @Test
    void takesAUrlAsItIs() {
        assertThat(OpenApiSpec.location("https://graphnous.github.io/graphnous-schemas/openapi/v1.yaml"))
            .isEqualTo("https://graphnous.github.io/graphnous-schemas/openapi/v1.yaml");
        assertThat(OpenApiSpec.location("file:/schemas/openapi/v1.yaml")).isEqualTo("file:/schemas/openapi/v1.yaml");
    }

    @Test
    void takesAPathAsTheUrlOfTheFile() {
        assertThat(OpenApiSpec.location("/schemas/openapi/v1.yaml"))
            .isEqualTo(Path.of("/schemas/openapi/v1.yaml").toUri().toString());
        assertThat(OpenApiSpec.location("schemas/../openapi/v1.yaml"))
            .isEqualTo(Path.of("openapi/v1.yaml").toAbsolutePath().toUri().toString());
    }

    @Test
    void takesThePublishedSpecWithoutOne() {
        assertThat(OpenApiSpec.location(" ")).startsWith("https://graphnous.github.io/");
    }
}
