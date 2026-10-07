package dev.graphnous.application.notification;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;

/**
 * @param projectId the project it is about, or {@code null} for the system
 * @param scanId    the scan it is about, or {@code null}; of the project
 * @param title     a short summary, or {@code null}
 */
public record CreateNotificationCommand(
    System.SystemId systemId,
    Project.ProjectId projectId,
    Scan.ScanId scanId,
    String title,
    String content
) {
}
