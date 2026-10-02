package dev.graphnous.scanner.typescript;

import dev.graphnous.scanner.definition.ScannerDefinition;
import dev.graphnous.scanner.model.ScanTarget;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TypescriptScannerDefinition implements ScannerDefinition {

    /**
     * The Node version used when the target's version is unknown, for
     * example when its version could not be detected while planning.
     */
    public static final String DEFAULT_NODE_VERSION = "24";

    private final Path script;

    private final String fallbackNodeVersion;

    public TypescriptScannerDefinition(final Path script) {
        this(script, DEFAULT_NODE_VERSION);
    }

    public TypescriptScannerDefinition(
        final Path script,
        final String fallbackNodeVersion
    ) {
        this.script = script;
        this.fallbackNodeVersion = fallbackNodeVersion;
    }

    @Override
    public boolean supports(final ScanTarget target) {
        return target.getLanguage().equals(ScanTarget.Language.TYPESCRIPT);
    }

    @Override
    public Path scanner() {
        return script;
    }

    @Override
    public List<String> command(
        final Path repository,
        final ScanTarget target
    ) {
        return command(script.toString(), repository.toString(), target);
    }

    @Override
    public List<String> containerCommand(
        final String scanner,
        final String repository,
        final ScanTarget target
    ) {
        return command(scanner, repository, target);
    }

    @Override
    public String image(final ScanTarget target) {
        final var version = target.getLanguageVersion();

        return "node:" + (version == null || version.isBlank() ? fallbackNodeVersion : version);
    }

    private List<String> command(
        final String script,
        final String repository,
        final ScanTarget target
    ) {
        final var command = new ArrayList<String>();

        command.add("node");
        command.add(script);

        command.add("--path");
        command.add(repository);

        command.add("--target");
        command.add(target.getPath());

        return command;
    }
}
