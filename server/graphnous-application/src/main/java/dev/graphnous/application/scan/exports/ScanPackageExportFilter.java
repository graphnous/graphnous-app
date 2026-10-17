package dev.graphnous.application.scan.exports;

import dev.graphnous.application.scan.ScanListing;

/**
 * Which of what a scan uses of packages to list; a null filter does not
 * filter.
 *
 * @param search part of the qualified name, ignoring case
 * @param pkg    the name of the package they are of
 */
public record ScanPackageExportFilter(
    String search,
    String pkg
) {

    public ScanPackageExportFilter {
        search = ScanListing.filter(search);
        pkg = ScanListing.filter(pkg);
    }

    public static ScanPackageExportFilter none() {
        return new ScanPackageExportFilter(null, null);
    }
}
