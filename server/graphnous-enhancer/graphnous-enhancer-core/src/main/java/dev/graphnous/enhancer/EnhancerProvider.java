package dev.graphnous.enhancer;

import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.core.model.ScanTarget.Language;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Enhances scan results with the installed enhancers.
 */
public class EnhancerProvider {

    private final EnhancerRegistry enhancerRegistry;

    public EnhancerProvider(final EnhancerRegistry enhancerRegistry) {
        this.enhancerRegistry = enhancerRegistry;
    }

    /**
     * Runs the enhancers for each language of the scan result on it.
     *
     * @return what each enhancer added, in the order they ran
     */
    public List<Enhancements> enhanceScan(final ScanResult scanResult) {
        return languages(scanResult)
            .flatMap(language -> enhancerRegistry.enhancersFor(language).stream())
            .map(enhancer -> enhancer.enhance(scanResult))
            .toList();
    }

    /**
     * The languages of the scan result: its target's, then those of its
     * files that GraphNous scans, each once.
     */
    private static Stream<Language> languages(final ScanResult scanResult) {
        final var target = Stream.ofNullable(scanResult.getTarget())
            .map(ScanTarget::getLanguage);

        final var files = scanResult.getModules()
            .stream()
            .flatMap(module -> module.getFiles().stream())
            .map(file -> language(file.getLanguage()));

        return Stream.concat(target, files)
            .filter(Objects::nonNull)
            .distinct();
    }

    /**
     * The language a file names, in any case ("java"); null when GraphNous
     * does not know it.
     */
    private static Language language(final String name) {
        if (name == null) {
            return null;
        }

        return Stream.of(Language.values())
            .filter(language -> language.value().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
}
