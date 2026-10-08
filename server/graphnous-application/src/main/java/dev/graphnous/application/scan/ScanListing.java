package dev.graphnous.application.scan;

import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.pagination.PageQuery;

import java.util.Set;

/**
 * What the lists of a scan's files, classes, methods and dependencies share:
 * which properties they sort by, and their optional filters.
 */
public final class ScanListing {

    private ScanListing() {
    }

    /**
     * The page, if it sorts by one of the properties.
     */
    public static PageQuery sorted(final PageQuery page, final Set<String> properties) {
        if (!properties.contains(page.sort().property())) {
            throw new ValidationException(
                "sort must be one of " + properties.stream().sorted().toList()
            );
        }

        return page;
    }

    /**
     * A filter as given, trimmed; null when it is blank, which does not
     * filter.
     */
    public static String filter(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
