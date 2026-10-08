package dev.graphnous.application.scan.dependencies;

/**
 * A library a module of the scan depends on.
 *
 * @param id     the library's id in the scan's graph, which focuses the
 *               graph on it; libraries are shared between scans, so it is
 *               the same in every scan
 * @param module the path of the module that depends on it
 * @param name   such as {@code org.slf4j:slf4j-api}
 * @param scope  such as compile or test, if the build system has scopes
 */
public record ScanDependency(
    String id,
    String module,
    String name,
    String version,
    String scope
) {
}
