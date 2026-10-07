package dev.graphnous.application.project;

import dev.graphnous.application.notification.NotificationRepository;
import dev.graphnous.application.scan.ScanDeleter;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.system.System;

/**
 * Deletes projects with their scans and notifications. Used by both the project and the
 * system service, which is why it depends on repositories only.
 */
public class ProjectDeleter {

    private final ProjectRepository projectRepository;
    private final ScanDeleter scanDeleter;
    private final NotificationRepository notificationRepository;

    public ProjectDeleter(
        final ProjectRepository projectRepository,
        final ScanDeleter scanDeleter,
        final NotificationRepository notificationRepository
    ) {
        this.projectRepository = projectRepository;
        this.scanDeleter = scanDeleter;
        this.notificationRepository = notificationRepository;
    }

    public void deleteProject(
        final System.SystemId systemId,
        final Project.ProjectId projectId
    ) {
        scanDeleter.requireNoActiveScans(projectId);

        scanDeleter.deleteScans(projectId);

        notificationRepository.deleteByProjectId(projectId);

        projectRepository.delete(systemId, projectId);
    }

    /**
     * Deletes every project of the system, or none: all projects are checked
     * for active scans before any is deleted.
     */
    public void deleteProjects(final System.SystemId systemId) {
        final var projectIds = projectRepository.findIdsBySystemId(systemId);

        for (final var projectId : projectIds) {
            scanDeleter.requireNoActiveScans(projectId);
        }

        for (final var projectId : projectIds) {
            deleteProject(systemId, projectId);
        }
    }
}
