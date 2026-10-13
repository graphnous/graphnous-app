package dev.graphnous.scanner.definition;

import dev.graphnous.core.model.ScanTarget;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A scanner as configured: the image for one language, its command and
 * where it writes its result. The command's arguments may hold these
 * placeholders, replaced for each target:
 * <ul>
 *     <li>{@value #REPOSITORY}: where the repository is mounted</li>
 *     <li>{@value #TARGET}: the target's path in the repository</li>
 *     <li>{@value #LANGUAGE_VERSION}: the target's language version, empty
 *     when unknown</li>
 *     <li>{@value #OUTPUT}: the output, where the result is read from</li>
 * </ul>
 *
 * @param language the language of the targets the scanner scans
 * @param image    the image the scanner runs in
 * @param command  the command running the scanner; it replaces the image's
 *                 entrypoint
 * @param output      the absolute container path the scanner writes its
 *                    result to, in a directory such as {@code /output}
 * @param mounts      what the container mounts besides the repository
 * @param environment the environment variables of the container
 */
public record ImageScannerDefinition(
    ScanTarget.Language language,
    String image,
    List<String> command,
    String output,
    List<ScannerMount> mounts,
    Map<String, String> environment
) implements ScannerDefinition {

    public static final String REPOSITORY = "{repository}";
    public static final String TARGET = "{target}";
    public static final String LANGUAGE_VERSION = "{languageVersion}";
    public static final String OUTPUT = "{output}";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z]+}");

    private static final List<String> PLACEHOLDERS = List.of(REPOSITORY, TARGET, LANGUAGE_VERSION, OUTPUT);

    public ImageScannerDefinition {
        Objects.requireNonNull(language, "The scanner needs a language");

        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("The " + language + " scanner needs an image");
        }

        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("The " + language + " scanner needs a command");
        }

        // In a directory of its own, which the sandbox creates writable for
        // whichever user the image runs as
        if (output == null || output.lastIndexOf('/') <= 0 || output.endsWith("/")) {
            throw new IllegalArgumentException(
                "The " + language + " scanner's output must be an absolute file path in a directory, got " + output
            );
        }

        command = List.copyOf(command);
        mounts = mounts == null ? List.of() : List.copyOf(mounts);
        environment = environment == null ? Map.of() : Map.copyOf(environment);

        final var outputDirectory = output.substring(0, output.lastIndexOf('/'));

        for (final var mount : mounts) {
            // The sandbox creates the output directory itself
            final var prefix = mount.target().endsWith("/") ? mount.target() : mount.target() + "/";

            if (mount.target().equals(outputDirectory) || outputDirectory.startsWith(prefix)) {
                throw new IllegalArgumentException(
                    "The " + language + " scanner's mount at " + mount.target() + " would hide its output " + output
                );
            }
        }

        for (final var name : environment.keySet()) {
            if (name.isBlank() || name.contains("=")) {
                throw new IllegalArgumentException(
                    "The " + language + " scanner's environment has an invalid variable name: " + name
                );
            }
        }

        for (final var argument : command) {
            final var matcher = PLACEHOLDER.matcher(argument);

            while (matcher.find()) {
                if (!PLACEHOLDERS.contains(matcher.group())) {
                    throw new IllegalArgumentException(
                        "Unknown placeholder " + matcher.group() + " in the " + language
                        + " scanner's command; expected one of " + String.join(", ", PLACEHOLDERS)
                    );
                }
            }
        }
    }

    /**
     * A scanner without mounts or environment variables of its own.
     */
    public ImageScannerDefinition(
        final ScanTarget.Language language,
        final String image,
        final List<String> command,
        final String output
    ) {
        this(language, image, command, output, List.of(), Map.of());
    }

    @Override
    public boolean supports(final ScanTarget target) {
        return target.getLanguage() == language;
    }

    @Override
    public List<String> command(
        final String repository,
        final ScanTarget target
    ) {
        final var values = Map.of(
            REPOSITORY, repository,
            TARGET, Objects.requireNonNullElse(target.getPath(), ""),
            LANGUAGE_VERSION, Objects.requireNonNullElse(target.getLanguageVersion(), ""),
            OUTPUT, output
        );

        return command.stream()
            .map(argument -> PLACEHOLDER.matcher(argument).replaceAll(match ->
                Matcher.quoteReplacement(values.get(match.group()))
            ))
            .toList();
    }
}
