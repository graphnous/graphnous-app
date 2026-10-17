package dev.graphnous.application.scan;

import dev.graphnous.application.event.ApplicationEvent;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

/**
 * A scan failed: while it ran, or as recovery found it would never finish.
 *
 * @param reason why, such as the error of the step it failed in
 */
public record ScanFailedEvent(
    Scan.ScanId scanId,
    Project.ProjectId projectId,
    String reason
) implements ApplicationEvent {
}
