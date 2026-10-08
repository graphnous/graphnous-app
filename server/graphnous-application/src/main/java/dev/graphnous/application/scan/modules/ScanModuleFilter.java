package dev.graphnous.application.scan.modules;

import dev.graphnous.application.scan.ScanListing;

/**
 * Which of a scan's modules to list; a null filter does not filter.
 *
 * @param search part of the name or path, ignoring case
 * @param target the path of the scan target
 */
public record ScanModuleFilter(
    String search,
    String target
) {

    public ScanModuleFilter {
        search = ScanListing.filter(search);
        target = ScanListing.filter(target);
    }

    public static ScanModuleFilter none() {
        return new ScanModuleFilter(null, null);
    }
}
