package dev.graphnous.application.scan;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

import java.util.List;
import java.util.Set;

/**
 * @param enhancers the names of the enhancers to run on the scan's results;
 *                  null for every installed enhancer, empty for none
 * @param excludes  the patterns of the files to leave out of the scan's
 *                  results, such as {@code *.spec.ts}; null for the ones
 *                  its project gives its scans, empty to keep every file
 * @param languages the languages to scan, such as {@code JAVA}; null for
 *                  the ones its project scans, empty for every language
 * @param trigger   why the scan starts; MANUAL when null
 */
public record CreateScanCommand(
    Project.ProjectId projectId,
    String revision,
    String branch,
    Set<String> enhancers,
    List<String> excludes,
    Set<String> languages,
    Scan.Trigger trigger
) {

    public CreateScanCommand {
        enhancers = enhancers == null ? null : Set.copyOf(enhancers);
        excludes = excludes == null ? null : List.copyOf(excludes);
        languages = languages == null ? null : Set.copyOf(languages);
        trigger = trigger == null ? Scan.Trigger.MANUAL : trigger;
    }

    /**
     * A scan started in the app, or through the API.
     */
    public CreateScanCommand(
        final Project.ProjectId projectId,
        final String revision,
        final String branch,
        final Set<String> enhancers,
        final List<String> excludes,
        final Set<String> languages
    ) {
        this(projectId, revision, branch, enhancers, excludes, languages, Scan.Trigger.MANUAL);
    }

    /**
     * A scan of the languages its project says.
     */
    public CreateScanCommand(
        final Project.ProjectId projectId,
        final String revision,
        final String branch,
        final Set<String> enhancers,
        final List<String> excludes
    ) {
        this(projectId, revision, branch, enhancers, excludes, null);
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
        this(projectId, revision, branch, enhancers, null, null);
    }

    /**
     * A scan that every installed enhancer runs on.
     */
    public CreateScanCommand(
        final Project.ProjectId projectId,
        final String revision,
        final String branch
    ) {
        this(projectId, revision, branch, null, null, null);
    }
}
