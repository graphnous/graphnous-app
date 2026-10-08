package dev.graphnous.application.scan.classes;

import dev.graphnous.application.scan.ScanListing;

/**
 * Which of a scan's classes to list; a null filter does not filter.
 *
 * @param search part of the name or qualified name, ignoring case
 * @param module the path of the module that declares them
 * @param kind   CLASS, INTERFACE, ENUM, RECORD, ANNOTATION, STRUCT, TRAIT or
 *               TYPE_ALIAS, ignoring case
 */
public record ScanClassFilter(
    String search,
    String module,
    String kind
) {

    public ScanClassFilter {
        search = ScanListing.filter(search);
        module = ScanListing.filter(module);
        kind = ScanListing.filter(kind);
    }

    public static ScanClassFilter none() {
        return new ScanClassFilter(null, null, null);
    }
}
