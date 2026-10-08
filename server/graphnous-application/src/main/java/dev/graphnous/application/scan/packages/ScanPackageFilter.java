package dev.graphnous.application.scan.packages;

import dev.graphnous.application.scan.ScanListing;

/**
 * Which of a scan's packages to list; a null filter does not filter.
 *
 * @param search part of the name or qualified name, ignoring case
 * @param module the path of the module they are in
 */
public record ScanPackageFilter(
    String search,
    String module
) {

    public ScanPackageFilter {
        search = ScanListing.filter(search);
        module = ScanListing.filter(module);
    }

    public static ScanPackageFilter none() {
        return new ScanPackageFilter(null, null);
    }
}
