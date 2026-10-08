package dev.graphnous.application.scan.methods;

import dev.graphnous.application.scan.ScanListing;

/**
 * Which of a scan's methods to list; a null filter does not filter.
 *
 * @param search    part of the name or qualified name, ignoring case
 * @param module    the path of the module that declares them
 * @param kind      METHOD, CONSTRUCTOR or FUNCTION, ignoring case
 * @param className the qualified name of the class they belong to
 */
public record ScanMethodFilter(
    String search,
    String module,
    String kind,
    String className
) {

    public ScanMethodFilter {
        search = ScanListing.filter(search);
        module = ScanListing.filter(module);
        kind = ScanListing.filter(kind);
        className = ScanListing.filter(className);
    }

    public static ScanMethodFilter none() {
        return new ScanMethodFilter(null, null, null, null);
    }
}
