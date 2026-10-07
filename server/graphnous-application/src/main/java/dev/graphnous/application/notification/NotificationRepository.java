package dev.graphnous.application.notification;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.notification.Notification;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;

public interface NotificationRepository {

    Notification save(
        final Notification notification
    );

    /**
     * @throws dev.graphnous.application.exception.NotFoundException when
     *                                                                there is none
     */
    Notification findById(
        final Notification.NotificationId id
    );

    /**
     * Every notification of the system, its projects and their scans.
     */
    Page<Notification> findAll(
        final System.SystemId systemId,
        final PageQuery pageQuery
    );

    /**
     * Every notification of the project and its scans.
     */
    Page<Notification> findAll(
        final Project.ProjectId projectId,
        final PageQuery pageQuery
    );

    Page<Notification> findAll(
        final Scan.ScanId scanId,
        final PageQuery pageQuery
    );

    void delete(
        final Notification.NotificationId id
    );

    void deleteBySystemId(
        final System.SystemId systemId
    );

    void deleteByProjectId(
        final Project.ProjectId projectId
    );

    void deleteByScanId(
        final Scan.ScanId scanId
    );
}
