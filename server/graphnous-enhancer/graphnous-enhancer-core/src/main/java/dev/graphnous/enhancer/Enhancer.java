package dev.graphnous.enhancer;

import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget.Language;

/**
 * Adds to what a scanner found in a scan result, such as the endpoints of a
 * Spring application.
 */
public interface Enhancer {

    /**
     * The name of this enhancer, e.g. {@code spring}; unique among the
     * installed enhancers.
     */
    String name();

    /**
     * The version of this enhancer, e.g. {@code 1.0.0}.
     */
    String version();

    /**
     * Enhances a scan result.
     *
     * @param scanResult the result a scanner produced for one scan target
     * @return what this enhancer adds to the scan result, by its
     *         {@link #name()} and {@link #version()}
     */
    Enhancements enhance(ScanResult scanResult);

    /**
     * The language of the scan results this enhancer enhances.
     */
    Language forLanguage();
}
