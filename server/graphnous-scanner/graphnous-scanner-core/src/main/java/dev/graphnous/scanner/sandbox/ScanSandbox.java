package dev.graphnous.scanner.sandbox;

import dev.graphnous.scanner.definition.ScannerDefinition;
import dev.graphnous.scanner.model.ScanResultSchema;
import dev.graphnous.scanner.model.ScanTarget;

import java.nio.file.Path;

public interface ScanSandbox {

    ScanResultSchema execute(
        final ScannerDefinition definition,
        final Path path,
        final ScanTarget target
    );

}

