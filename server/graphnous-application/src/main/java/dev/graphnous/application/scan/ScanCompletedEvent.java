package dev.graphnous.application.scan;

import dev.graphnous.application.event.ApplicationEvent;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.scanner.model.ScanResultSchema;

import java.util.List;

/**
 * A scan completed: its results are stored and enhanced. The results travel
 * with the event, so listeners need not read them back from the graph.
 */
public class ScanCompletedEvent implements ApplicationEvent {

    private final Scan.ScanId scanId;
    private final Project.ProjectId projectId;
    private final List<ScanResultSchema> results;

    public ScanCompletedEvent(
        final Scan.ScanId scanId,
        final Project.ProjectId projectId,
        final List<ScanResultSchema> results
    ) {
        this.scanId = scanId;
        this.projectId = projectId;
        this.results = List.copyOf(results);
    }

    public Scan.ScanId getScanId() {
        return this.scanId;
    }

    public Project.ProjectId getProjectId() {
        return this.projectId;
    }

    public List<ScanResultSchema> getResults() {
        return this.results;
    }
}
