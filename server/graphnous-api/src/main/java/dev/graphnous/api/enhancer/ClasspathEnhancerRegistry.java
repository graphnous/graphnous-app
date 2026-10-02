package dev.graphnous.api.enhancer;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.application.enhancer.EnhancerPipeline;
import dev.graphnous.application.enhancer.EnhancerRegistry;
import dev.graphnous.application.enhancer.model.EnhancerManifestSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The enhancers shipped with the server: every manifest matching the
 * location, by default the {@code .json} files in {@code enhancers/} on the
 * classpath (src/main/resources/enhancers).
 * <p>
 * The manifests are read and checked once, when the server starts, so a
 * manifest that does not parse, or enhancers that cannot be ordered, stop
 * the server instead of failing scans.
 */
public class ClasspathEnhancerRegistry implements EnhancerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ClasspathEnhancerRegistry.class);

    private final List<EnhancerManifestSchema> installed;

    /**
     * @param location     a resource pattern, such as
     *                     {@code classpath*:enhancers/*.json}
     * @param objectMapper a Jackson 2 mapper, as the manifest model uses
     */
    public ClasspathEnhancerRegistry(
        final String location,
        final ResourcePatternResolver resolver,
        final ObjectMapper objectMapper
    ) {
        final var manifests = new ArrayList<EnhancerManifestSchema>();

        for (final var resource : resources(location, resolver)) {
            manifests.add(read(resource, objectMapper));
        }

        this.installed = List.copyOf(EnhancerPipeline.order(manifests));

        log.info(
            "Installed {} enhancer(s) from {}: {}",
            installed.size(),
            location,
            installed.stream().map(EnhancerManifestSchema::getId).toList()
        );
    }

    @Override
    public List<EnhancerManifestSchema> installed() {
        return installed;
    }

    /**
     * The manifests in file name order, so they install in a stable order.
     */
    private static List<Resource> resources(
        final String location,
        final ResourcePatternResolver resolver
    ) {
        try {
            return Arrays.stream(resolver.getResources(location))
                .filter(Resource::isReadable)
                .sorted(Comparator.comparing(resource -> Objects.requireNonNullElse(resource.getFilename(), "")))
                .toList();
        } catch (final IOException e) {
            throw new IllegalStateException("Reading the enhancers at " + location + " failed", e);
        }
    }

    private static EnhancerManifestSchema read(
        final Resource resource,
        final ObjectMapper objectMapper
    ) {
        try (final var input = resource.getInputStream()) {
            return objectMapper.readValue(input, EnhancerManifestSchema.class);
        } catch (final IOException e) {
            throw new IllegalStateException(
                "Enhancer manifest " + resource.getFilename() + " is not valid: " + e.getMessage(),
                e
            );
        }
    }
}
