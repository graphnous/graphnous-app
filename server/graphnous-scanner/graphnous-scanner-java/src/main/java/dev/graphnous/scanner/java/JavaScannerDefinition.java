package dev.graphnous.scanner.java;

import dev.graphnous.scanner.definition.ScannerDefinition;
import dev.graphnous.scanner.model.ScanTarget;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JavaScannerDefinition implements ScannerDefinition {

    /**
     * The runtime of the scanner jar itself, which is built for Java 25.
     * The Java version of the scanned project does not matter: the scanner
     * only parses its sources.
     */
    static final String IMAGE = "eclipse-temurin:25-jre";

    private final Path scannerJar;

    private final boolean verbose;

    public JavaScannerDefinition(final Path scannerJar) {
        this(scannerJar, false);
    }

    /**
     * @param verbose whether the scanner reports every file instead of
     *                one line per module
     */
    public JavaScannerDefinition(
        final Path scannerJar,
        final boolean verbose
    ) {
        this.scannerJar = scannerJar;
        this.verbose = verbose;
    }

    @Override
    public boolean supports(final ScanTarget target) {
        return target.getLanguage() == ScanTarget.Language.JAVA;
    }

    @Override
    public Path scanner() {
        return scannerJar;
    }

    @Override
    public List<String> command(
        final Path repository,
        final ScanTarget target
    ) {
        final var java = Path.of(
            System.getProperty("java.home"),
            "bin",
            "java"
        );

        return command(
            java.toString(),
            scannerJar.toString(),
            repository.toString(),
            target
        );
    }

    @Override
    public List<String> containerCommand(
        final String scanner,
        final String repository,
        final ScanTarget target
    ) {
        return command("java", scanner, repository, target);
    }

    @Override
    public String image(final ScanTarget target) {
        return IMAGE;
    }

    private List<String> command(
        final String java,
        final String scanner,
        final String repository,
        final ScanTarget target
    ) {
        final var commands = new ArrayList<String>();

        commands.add(java);
        commands.add("-jar");
        commands.add(scanner);

        commands.add("--path");
        commands.add(repository);

        commands.add("--target");
        commands.add(target.getPath());

        if (target.getLanguageVersion() != null) {
            commands.add("--java-version");
            commands.add(target.getLanguageVersion());
        }

        if (verbose) {
            commands.add("--verbose");
        }

        return commands;
    }
}
