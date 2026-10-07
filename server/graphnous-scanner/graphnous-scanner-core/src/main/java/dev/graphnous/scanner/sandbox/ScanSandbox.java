package dev.graphnous.scanner.sandbox;

import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.scanner.definition.ScannerDefinition;

import java.nio.file.Path;

public interface ScanSandbox {

    ScanResult execute(
        final ScannerDefinition definition,
        final Path path,
        final ScanTarget target
    );

}

