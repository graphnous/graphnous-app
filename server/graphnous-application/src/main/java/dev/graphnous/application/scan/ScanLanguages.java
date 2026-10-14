package dev.graphnous.application.scan;

import dev.graphnous.application.exception.ValidationException;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The languages this server has a scanner for, such as {@code JAVA} and
 * {@code TYPESCRIPT}, and the ones a project or scan is to scan, as they
 * are kept: none for every language a scan finds.
 */
public final class ScanLanguages {

    private final Set<String> available;

    /**
     * @param available the languages this server has a scanner for
     */
    public ScanLanguages(final Collection<String> available) {
        this.available = Set.copyOf(new TreeSet<>(available));
    }

    /**
     * The languages this server has a scanner for, by name.
     */
    public List<String> available() {
        return this.available.stream().sorted().toList();
    }

    /**
     * The languages as they are kept: trimmed, in capitals, each once,
     * without the blank ones; none for null, which scans every language.
     *
     * @throws ValidationException when this server has no scanner for one
     */
    public Set<String> normalize(final Collection<String> languages) {
        if (languages == null) {
            return Set.of();
        }

        final var normalized = new TreeSet<String>();

        for (final var language : languages) {
            if (language == null || language.isBlank()) {
                continue;
            }

            normalized.add(language.trim().toUpperCase(Locale.ROOT));
        }

        final var unknown = normalized.stream().filter(language -> !this.available.contains(language)).toList();

        if (!unknown.isEmpty()) {
            throw new ValidationException(
                "No scanner for " + String.join(", ", unknown) + "; there are scanners for "
                + String.join(", ", available())
            );
        }

        return Set.copyOf(normalized);
    }

    /**
     * The languages kept before, without the ones this server no longer
     * has a scanner for, such as for a project whose setting is older
     * than this server's scanners.
     */
    public Set<String> current(final Collection<String> languages) {
        if (languages == null) {
            return Set.of();
        }

        return languages.stream()
            .filter(this.available::contains)
            .collect(Collectors.toUnmodifiableSet());
    }
}
