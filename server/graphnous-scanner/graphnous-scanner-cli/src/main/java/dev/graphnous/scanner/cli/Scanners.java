package dev.graphnous.scanner.cli;

import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.scanner.definition.ImageScannerDefinition;
import dev.graphnous.scanner.definition.ScannerDefinition;

import java.util.ArrayList;
import java.util.List;

import static dev.graphnous.scanner.definition.ImageScannerDefinition.LANGUAGE_VERSION;
import static dev.graphnous.scanner.definition.ImageScannerDefinition.OUTPUT;
import static dev.graphnous.scanner.definition.ImageScannerDefinition.REPOSITORY;
import static dev.graphnous.scanner.definition.ImageScannerDefinition.TARGET;

/**
 * The scanners the CLI runs: the same images and commands as the server's
 * graphnous.scanners configuration.
 */
final class Scanners {

    static final String JAVA_IMAGE = "ghcr.io/graphnous/graphnous-java-scanner:0.3.1";
    static final String TYPESCRIPT_IMAGE = "ghcr.io/graphnous/graphnous-typescript-scanner:0.2.1";

    private static final String RESULT = "/output/scan-result.json";

    private Scanners() {
    }

    static List<ScannerDefinition> of(final CliOptions options) {
        final var javaCommand = new ArrayList<>(List.of(
            // As the server runs it: resolving calls into the project's
            // libraries needs more than the JVM's default quarter
            "java", "-XX:MaxRAMPercentage=75", "-jar", "/opt/graphnous/java-scanner.jar",
            "--path", REPOSITORY,
            "--target", TARGET,
            "--java-version", LANGUAGE_VERSION,
            "--output", OUTPUT
        ));

        final var typescriptCommand = new ArrayList<>(List.of(
            "node", "/opt/graphnous/scanner.js",
            "--path", REPOSITORY,
            "--target", TARGET,
            "--output", OUTPUT
        ));

        if (options.verbose()) {
            javaCommand.add("--verbose");
            typescriptCommand.add("--verbose");
        }

        return List.of(
            new ImageScannerDefinition(ScanTarget.Language.JAVA, options.javaScannerImage(), javaCommand, RESULT),
            new ImageScannerDefinition(ScanTarget.Language.TYPESCRIPT, options.typescriptScannerImage(), typescriptCommand, RESULT)
        );
    }
}
