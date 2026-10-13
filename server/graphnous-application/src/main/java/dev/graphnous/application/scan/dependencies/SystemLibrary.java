package dev.graphnous.application.scan.dependencies;

import dev.graphnous.domain.project.Project;

import java.util.List;

/**
 * A library the projects of a system depend on, with every version of it
 * they use: more than one version is drift between them.
 *
 * @param name     such as {@code org.slf4j:slf4j-api}
 * @param versions each version in use, with the projects that use it
 */
public record SystemLibrary(
    String name,
    List<Version> versions
) {

    public SystemLibrary {
        versions = List.copyOf(versions);
    }

    /**
     * @param version  null when the library is used without one
     * @param projects the projects with a module that depends on it
     */
    public record Version(
        String version,
        List<Project.ProjectId> projects
    ) {

        public Version {
            projects = List.copyOf(projects);
        }
    }
}
