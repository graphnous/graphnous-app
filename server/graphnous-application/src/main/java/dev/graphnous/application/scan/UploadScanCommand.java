package dev.graphnous.application.scan;

import dev.graphnous.domain.project.Project;
import dev.graphnous.scanner.model.ScanResultSchema;

import java.util.List;

/**
 * Scan results produced elsewhere, such as by a CI job or a build plugin,
 * for the given revision of the project.
 */
public record UploadScanCommand(
    Project.ProjectId projectId,
    String revision,
    String branch,
    List<ScanResultSchema> results
) {

    public UploadScanCommand {
        results = List.copyOf(results);
    }
}
