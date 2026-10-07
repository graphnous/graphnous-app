package dev.graphnous.enhancer;

import java.util.List;
import java.util.Objects;

/**
 * What an enhancer adds to a scan result.
 *
 * @param name         the name of the enhancer
 * @param version      the version of the enhancer
 * @param enhancements each addition, in the order the enhancer made them
 */
public record Enhancements(
    String name,
    String version,
    List<Enhancement> enhancements
) {

    public Enhancements {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        enhancements = List.copyOf(enhancements);
    }

    /**
     * What an enhancer that found nothing to add returns.
     */
    public static Enhancements none(final Enhancer enhancer) {
        return new Enhancements(enhancer.name(), enhancer.version(), List.of());
    }
}
