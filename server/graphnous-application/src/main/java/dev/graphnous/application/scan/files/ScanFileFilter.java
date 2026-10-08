package dev.graphnous.application.scan.files;

import dev.graphnous.application.scan.ScanListing;

/**
 * Which of a scan's files to list; a null filter does not filter.
 *
 * @param search    part of the path, ignoring case
 * @param module    the path of the module
 * @param language  the language, ignoring case
 * @param sourceSet MAIN or TEST
 */
public record ScanFileFilter(
    String search,
    String module,
    String language,
    String sourceSet
) {

    public ScanFileFilter {
        search = ScanListing.filter(search);
        module = ScanListing.filter(module);
        language = ScanListing.filter(language);
        sourceSet = ScanListing.filter(sourceSet);
    }

    public static ScanFileFilter none() {
        return new ScanFileFilter(null, null, null, null);
    }
}
