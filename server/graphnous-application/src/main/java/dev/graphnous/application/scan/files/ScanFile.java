package dev.graphnous.application.scan.files;

/**
 * A source file a scan found.
 *
 * @param id        the file's id in the scan's graph, which focuses the
 *                  graph on it
 * @param target    the path of the scan target the file is in
 * @param module    the path of the module the file is in
 * @param path      the path within the module
 * @param sourceSet MAIN or TEST, if the scanner could tell
 * @param size      in bytes, if known
 * @param classes   the top-level classes it declares
 * @param functions the functions it declares outside any class
 */
public record ScanFile(
    String id,
    String target,
    String module,
    String path,
    String language,
    String sourceSet,
    Long size,
    String checksum,
    long classes,
    long functions
) {
}
