package dev.graphnous.application.scan.result;

import dev.graphnous.core.model.ScanResult;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Leaves files out of scan results before they are stored, such as tests:
 * a file left out takes the classes, functions and variables it declares
 * with it, so they are in no graph, stats or enhancement of the scan.
 */
public final class ScanResultFilter {

    private ScanResultFilter() {
    }

    /**
     * Removes the files whose path fits one of the patterns from the
     * results, in place. Paths are from the root of the repository: the
     * target's, the module's and the file's.
     *
     * @return how many files were left out
     * @throws IllegalArgumentException when a pattern is not well formed
     */
    public static int exclude(
        final List<ScanResult> results,
        final List<String> patterns
    ) {
        if (patterns == null || patterns.isEmpty()) {
            return 0;
        }

        final var globs = PathGlob.all(patterns);

        if (globs.isEmpty()) {
            return 0;
        }

        var excluded = 0;

        for (final var result : results) {
            final var target = result.getTarget() == null ? null : result.getTarget().getPath();

            for (final var module : result.getModules()) {
                final var before = module.getFiles().size();

                module.getFiles().removeIf(file -> PathGlob.anyMatches(globs, path(target, module.getPath(), file.getPath())));

                excluded += before - module.getFiles().size();
            }
        }

        return excluded;
    }

    /**
     * The path from the repository's root, leaving out the {@code .} of a
     * target or module at the root of its parent.
     */
    static String path(final String... segments) {
        return Arrays.stream(segments)
            .filter(segment -> segment != null && !segment.isBlank() && !segment.equals("."))
            .map(segment -> segment.replaceAll("^\\./|/+$", ""))
            .collect(Collectors.joining("/"));
    }
}
