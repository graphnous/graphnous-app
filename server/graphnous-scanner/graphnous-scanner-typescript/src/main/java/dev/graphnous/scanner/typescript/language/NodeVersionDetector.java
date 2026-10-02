package dev.graphnous.scanner.typescript.language;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.scanner.language.LanguageVersionDetector;
import dev.graphnous.scanner.model.ScanTarget;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class NodeVersionDetector implements LanguageVersionDetector {

    /**
     * The Node version reported when the target names none.
     */
    public static final String DEFAULT_NODE_VERSION = "24";

    /**
     * A single semver comparator, e.g. {@code >=18.0.0}, {@code ^v20} or {@code 18.x}.
     */
    private static final Pattern COMPARATOR = Pattern.compile(
        "(<=|>=|<|>|=|\\^|~)?v?(\\d+)(?:\\.(\\d+|[xX*]))?(?:\\.(\\d+|[xX*]))?"
    );

    private final ObjectMapper objectMapper;

    private final String fallbackVersion;

    public NodeVersionDetector(
        final ObjectMapper objectMapper,
        final String fallbackVersion
    ) {
        this.objectMapper = objectMapper;

        this.fallbackVersion = fallbackVersion;
    }

    @Override
    public boolean supports(final ScanTarget target) {
        return target.getLanguage().equals(ScanTarget.Language.TYPESCRIPT);
    }

    @Override
    public String detect(
        final Path repository,
        final ScanTarget target
    ) {
        final var targetPath = repository.resolve(target.getPath());

        var version = detectFromFile(
            targetPath.resolve(".nvmrc")
        );

        if (version != null) {
            return version;
        }

        version = detectFromFile(
            targetPath.resolve(".node-version")
        );

        if (version != null) {
            return version;
        }

        version = detectFromPackageJson(
            targetPath.resolve("package.json")
        );

        if (version != null) {
            return version;
        }

        return fallbackVersion;
    }

    private String detectFromFile(final Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }

        try {
            return normalize(
                Files.readString(file).trim()
            );
        } catch (IOException e) {
            throw new UncheckedIOException(
                "Failed to read " + file,
                e
            );
        }
    }

    private String detectFromPackageJson(final Path packageJson) {
        if (!Files.isRegularFile(packageJson)) {
            return null;
        }

        try {
            final var root = objectMapper.readTree(
                Files.newInputStream(packageJson)
            );

            final var engines = root.get("engines");

            if (engines == null) {
                return null;
            }

            final var node = engines.get("node");

            if (node == null) {
                return null;
            }

            return normalize(node.asText());

        } catch (IOException e) {
            throw new UncheckedIOException(
                "Failed to read " + packageJson,
                e
            );
        }
    }

    /**
     * Turns a version or semver range into a version usable as a
     * {@code node:<version>} image tag, or {@code null} when the value
     * has no lower bound to pick (such as {@code lts/*}, {@code *} or
     * {@code <20}).
     *
     * <ul>
     *     <li>{@code 20.11.1}, {@code v20.11.1} → {@code 20.11.1}</li>
     *     <li>{@code 18.x}, {@code 18.*} → {@code 18}</li>
     *     <li>{@code ^20.11.0}, {@code >=18.0.0} → {@code 20}, {@code 18}</li>
     *     <li>{@code ~20.11.0} → {@code 20.11}</li>
     *     <li>{@code ^18 || ^20}, {@code 18 - 20} → first alternative, lower bound</li>
     * </ul>
     */
    private String normalize(final String version) {
        final var alternative = version.split("\\|\\|")[0].trim();

        for (final var comparator : alternative.split("\\s+")) {
            final var matcher = COMPARATOR.matcher(comparator);

            if (!matcher.matches()) {
                continue;
            }

            final var operator = matcher.group(1) == null ? "" : matcher.group(1);

            if (operator.startsWith("<")) {
                continue;
            }

            return toTag(
                operator,
                matcher.group(2),
                matcher.group(3),
                matcher.group(4)
            );
        }

        return null;
    }

    private String toTag(
        final String operator,
        final String major,
        final String minor,
        final String patch
    ) {
        return switch (operator) {
            case "^", ">", ">=" -> major;
            case "~" -> isNumber(minor) ? major + "." + minor : major;
            default -> {
                if (!isNumber(minor)) {
                    yield major;
                }

                yield isNumber(patch)
                    ? major + "." + minor + "." + patch
                    : major + "." + minor;
            }
        };
    }

    private boolean isNumber(final String part) {
        return part != null && part.chars().allMatch(Character::isDigit);
    }
}
