package dev.graphnous.application.scan;

import dev.graphnous.core.model.ScanResult;
import dev.graphnous.domain.project.Project;

import java.util.List;

/**
 * Scan results produced elsewhere, such as by a CI job or a build plugin,
 * for the given revision of the project.
 */
public record UploadScanCommand(
    Project.ProjectId projectId,
    String revision,
    String branch,
    List<ScanResult> results
) {

    public UploadScanCommand {
        results = List.copyOf(results);
    }
}
