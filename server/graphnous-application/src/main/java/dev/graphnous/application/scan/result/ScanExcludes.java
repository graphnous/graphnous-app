package dev.graphnous.application.scan.result;

import dev.graphnous.application.exception.ValidationException;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * The patterns of the files to leave out of a scan, as a project keeps
 * them and a scan is created with them; see {@link PathGlob} for how they
 * are written.
 */
public final class ScanExcludes {

    /**
     * How many patterns a project or scan can have.
     */
    public static final int MAX_PATTERNS = 50;

    private ScanExcludes() {
    }

    /**
     * The patterns as they are kept: trimmed, each once, in the order
     * given, without the blank ones; none for null.
     *
     * @throws ValidationException when there are too many, or one is not
     *                             well formed
     */
    public static List<String> normalize(final List<String> patterns) {
        if (patterns == null) {
            return List.of();
        }

        final var normalized = new LinkedHashSet<String>();

        for (final var pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }

            try {
                normalized.add(PathGlob.of(pattern).pattern());
            } catch (final IllegalArgumentException e) {
                throw new ValidationException(e.getMessage());
            }
        }

        if (normalized.size() > MAX_PATTERNS) {
            throw new ValidationException("At most " + MAX_PATTERNS + " patterns of files to leave out");
        }

        return List.copyOf(normalized);
    }
}
