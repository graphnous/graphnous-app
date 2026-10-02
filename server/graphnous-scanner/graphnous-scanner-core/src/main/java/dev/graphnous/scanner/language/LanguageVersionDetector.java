package dev.graphnous.scanner.language;

import dev.graphnous.scanner.model.ScanTarget;

import java.nio.file.Path;

public interface LanguageVersionDetector {

    boolean supports(
        final ScanTarget target
    );

    String detect(
        final Path repository,
        final ScanTarget target
    );
}
