package dev.graphnous.enhancer;

import dev.graphnous.core.model.ScanTarget.Language;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The enhancers installed on this server: every {@link Enhancer} bean.
 */
public class EnhancerRegistry {

    private final Map<Language, List<Enhancer>> enhancers;

    public EnhancerRegistry(final List<Enhancer> enhancers) {
        this.enhancers = enhancers.stream()
            .collect(Collectors.groupingBy(Enhancer::forLanguage, Collectors.toUnmodifiableList()));
    }

    /**
     * The enhancers for the language, in the order they were registered.
     */
    public List<Enhancer> enhancersFor(final Language language) {
        return enhancers.getOrDefault(language, List.of());
    }
}
