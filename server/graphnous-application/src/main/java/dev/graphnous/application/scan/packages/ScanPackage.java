package dev.graphnous.application.scan.packages;

/**
 * A package a scan found: a Java, Python or Go package, or a Rust module.
 *
 * @param id        the package's id in the scan's graph, which focuses the
 *                  graph on it
 * @param module    the path of the module the package is in
 * @param classes   the classes it contains, nested ones included
 * @param functions the functions its files declare outside any class
 * @param variables the variables its files declare outside any class
 */
public record ScanPackage(
    String id,
    String module,
    String name,
    String qualifiedName,
    long classes,
    long functions,
    long variables
) {
}
