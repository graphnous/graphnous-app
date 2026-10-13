package dev.graphnous.application.scan.dependencies;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

/**
 * A library a module of one of a system's projects depends on.
 *
 * @param projectId  the project the module is of
 * @param scanId     the scan of the project it was found in
 * @param dependency the library, and the module that depends on it
 */
public record SystemDependency(
    Project.ProjectId projectId,
    Scan.ScanId scanId,
    ScanDependency dependency
) {
}
