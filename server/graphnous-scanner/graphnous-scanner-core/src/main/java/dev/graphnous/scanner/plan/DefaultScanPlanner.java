package dev.graphnous.scanner.plan;

import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.scanner.language.LanguageVersionDetector;
import dev.graphnous.scanner.targetdetector.ScanTargetDetector;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultScanPlanner implements ScanPlanner {

    private final List<ScanTargetDetector> detectors;

    private final List<LanguageVersionDetector> versionDetectors;

    public DefaultScanPlanner(
        final List<ScanTargetDetector> detectors,
        final List<LanguageVersionDetector> versionDetectors
    ) {
        this.detectors = detectors;
        this.versionDetectors = versionDetectors;
    }

    @Override
    public ScanPlan plan(
        final Path path,
        final Set<ScanTarget.Language> languages
    ) {
        final var found = detectors.stream()
            .flatMap(detector -> detector.detect(path).stream())
            .toList();

        // Before their versions are looked for, which left out ones do not need
        final var scanned = found.stream()
            .collect(Collectors.partitioningBy(
                target -> languages.isEmpty() || languages.contains(target.getLanguage())
            ));
        final var targets = scanned.get(true);
        final var leftOut = scanned.get(false);

        final var warnings = new ArrayList<String>();

        for (final var target : targets) {
            target.setLanguageVersion(
                detectVersion(path, target, warnings)
            );
        }

        return new ScanPlan(targets, warnings, leftOut);
    }

    /**
     * Returns the language version, or {@code null} with a warning when it
     * cannot be determined: the target is still scanned without it.
     */
    private String detectVersion(
        final Path repository,
        final ScanTarget target,
        final List<String> warnings
    ) {
        final var detector = versionDetectors.stream()
            .filter(candidate -> candidate.supports(target))
            .findFirst();

        if (detector.isEmpty()) {
            return null;
        }

        try {
            return detector.get().detect(repository, target);
        } catch (RuntimeException e) {
            warnings.add(
                target.getPath() + ": could not determine the "
                + target.getLanguage() + " version, scanning without it ("
                + rootMessage(e) + ")"
            );

            return null;
        }
    }

    private static String rootMessage(final Throwable error) {
        var root = error;

        while (root.getCause() != null) {
            root = root.getCause();
        }

        return root.getMessage() == null
            ? root.getClass().getSimpleName()
            : root.getMessage();
    }
}
