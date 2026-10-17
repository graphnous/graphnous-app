package dev.graphnous.application.scan.exports;

/**
 * What a scan's code uses of a package it imports and does not scan, such
 * as {@code Button} of {@code @acme/theme}.
 *
 * @param id            its id in the scan's graph, which asking for its
 *                      usages takes
 * @param pkg           the package, such as {@code @acme/theme}
 * @param module        the module it is imported from as written: the
 *                      package, or a subpath such as
 *                      {@code @acme/theme/format}
 * @param name          the name it is exported by, such as {@code Button}
 *                      or {@code Icons.Clock}
 * @param qualifiedName the module and the name, such as
 *                      {@code @acme/theme:Button}
 * @param usages        the places that call, render or construct it
 * @param files         the files with such a place
 */
public record ScanPackageExport(
    String id,
    String pkg,
    String module,
    String name,
    String qualifiedName,
    long usages,
    long files
) {
}
