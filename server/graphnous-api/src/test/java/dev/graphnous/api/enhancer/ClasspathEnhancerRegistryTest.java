package dev.graphnous.api.enhancer;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.application.enhancer.model.EnhancerManifestSchema;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathEnhancerRegistryTest {

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void installsTheManifestsAtTheLocation() {
        final var registry = registry("classpath*:enhancer-registry/valid/*.json");

        assertThat(registry.installed())
            .extracting(EnhancerManifestSchema::getId)
            .containsExactly("io.acme.spring", "io.acme.http-links");
        assertThat(registry.installed().get(1).getScope()).isEqualTo(EnhancerManifestSchema.Scope.SCAN);
    }

    @Test
    void installsNothingWhenThereAreNoManifests() {
        assertThat(registry("classpath*:enhancer-registry/none/*.json").installed()).isEmpty();
    }

    @Test
    void refusesAManifestWithAnUnknownField() {
        assertThatThrownBy(() -> registry("classpath*:enhancer-registry/unknown-field/*.json"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("spring.json")
            .hasMessageContaining("macth");
    }

    @Test
    void refusesEnhancersThatCannotBeOrdered() {
        assertThatThrownBy(() -> registry("classpath*:enhancer-registry/missing-dependency/*.json"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Enhancer io.acme.http-links depends on io.acme.spring, which is not installed");
    }

    private ClasspathEnhancerRegistry registry(final String location) {
        return new ClasspathEnhancerRegistry(location, resolver, objectMapper);
    }
}
