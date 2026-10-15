package dev.graphnous.api;

import java.nio.file.Path;

/**
 * The OpenAPI spec controller tests check requests and responses against:
 * the one the build generates the API from, the graphnous.openapi.spec
 * property, so a local copy can be used without network access, e.g.
 * {@code mvn test -Dgraphnous.openapi.spec=../graphnous-schemas/openapi/v1.yaml}.
 */
public final class OpenApiSpec {

    private static final String PUBLISHED = "https://graphnous.github.io/graphnous-schemas/openapi/v1.yaml";

    /**
     * The URL of the spec; a file path is taken relative to the working
     * directory.
     */
    public static final String LOCATION = location(System.getProperty("graphnous.openapi.spec", PUBLISHED));

    private OpenApiSpec() {
    }

    static String location(final String spec) {
        if (spec.isBlank()) {
            return PUBLISHED;
        }

        return spec.matches("^[a-zA-Z][a-zA-Z0-9+.-]+:.*") && !spec.matches("^[a-zA-Z]:[\\\\/].*")
            ? spec
            : Path.of(spec).toAbsolutePath().normalize().toUri().toString();
    }
}
