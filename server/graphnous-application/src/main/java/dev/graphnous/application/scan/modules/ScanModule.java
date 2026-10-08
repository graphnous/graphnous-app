package dev.graphnous.application.scan.modules;

/**
 * A module a scan found, such as a Maven module or an npm package, with
 * how much it holds.
 *
 * @param id           the module's id in the scan's graph, which focuses
 *                     the graph on it
 * @param target       the path of the scan target the module is in
 * @param path         its path within the target
 * @param classes      each class of the module once, whether it is listed
 *                     by its file, its package or both
 * @param methods      the methods of its classes and the functions declared
 *                     outside any class
 * @param dependencies the libraries it depends on
 */
public record ScanModule(
    String id,
    String target,
    String name,
    String path,
    long files,
    long packages,
    long classes,
    long methods,
    long dependencies
) {
}
