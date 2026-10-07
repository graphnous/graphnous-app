package dev.graphnous.enhancer;

import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnhancerRegistryTest {

    @Test
    void returnsTheEnhancersForALanguageInRegisteredOrder() {
        final var spring = enhancer(Language.JAVA);
        final var react = enhancer(Language.TYPESCRIPT);
        final var jpa = enhancer(Language.JAVA);

        final var registry = new EnhancerRegistry(List.of(spring, react, jpa));

        assertThat(registry.enhancersFor(Language.JAVA)).containsExactly(spring, jpa);
        assertThat(registry.enhancersFor(Language.TYPESCRIPT)).containsExactly(react);
    }

    @Test
    void returnsNoEnhancersForALanguageWithout() {
        final var registry = new EnhancerRegistry(List.of(enhancer(Language.JAVA)));

        assertThat(registry.enhancersFor(Language.GO)).isEmpty();
    }

    private static Enhancer enhancer(final Language language) {
        return new Enhancer() {

            @Override
            public String name() {
                return language.value().toLowerCase();
            }

            @Override
            public String version() {
                return "1.0.0";
            }

            @Override
            public Enhancements enhance(final ScanResult scanResult) {
                return Enhancements.none(this);
            }

            @Override
            public Language forLanguage() {
                return language;
            }
        };
    }
}
