package dev.graphnous.application.scan;

import dev.graphnous.domain.project.Project;

import java.util.List;
import java.util.Set;

/**
 * @param enhancers the names of the enhancers to run on the scan's results;
 *                  null for every installed enhancer, empty for none
 * @param excludes  the patterns of the files to leave out of the scan's
 *                  results, such as {@code *.spec.ts}; null for the ones
 *                  its project gives its scans, empty to keep every file
 */
public record CreateScanCommand(
    Project.ProjectId projectId,
    String revision,
    String branch,
    Set<String> enhancers,
    List<String> excludes
) {

    public CreateScanCommand {
        enhancers = enhancers == null ? null : Set.copyOf(enhancers);
        excludes = excludes == null ? null : List.copyOf(excludes);
    }

    /**
     * A scan that leaves out the files its project says.
     */
    public CreateScanCommand(
        final Project.ProjectId projectId,
        final String revision,
        final String branch,
        final Set<String> enhancers
    ) {
        this(projectId, revision, branch, enhancers, null);
    }

    /**
     * A scan that every installed enhancer runs on.
     */
    public CreateScanCommand(
        final Project.ProjectId projectId,
        final String revision,
        final String branch
    ) {
        this(projectId, revision, branch, null, null);
    }
}
