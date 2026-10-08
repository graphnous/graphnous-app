package dev.graphnous.application.scan.dependencies;

import dev.graphnous.application.scan.ScanListing;

/**
 * Which of a scan's dependencies to list; a null filter does not filter.
 *
 * @param search part of the library's name, ignoring case
 * @param module the path of the module that depends on them
 * @param scope  such as compile or test, ignoring case
 */
public record ScanDependencyFilter(
    String search,
    String module,
    String scope
) {

    public ScanDependencyFilter {
        search = ScanListing.filter(search);
        module = ScanListing.filter(module);
        scope = ScanListing.filter(scope);
    }

    public static ScanDependencyFilter none() {
        return new ScanDependencyFilter(null, null, null);
    }
}
