package dev.graphnous.application.notification;

import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.notification.Notification;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;

/**
 * The notifications, and who read them: a notification is read or unread
 * for each of its recipients, the users who marked it, or no user where
 * there is none. It is unread for whoever did not mark it.
 * <p>
 * What it finds is read or not as its recipient has it; which one is the
 * {@link UserContext} it is given, by its id, null for no user.
 */
public interface NotificationRepository {

    /**
     * Stores the notification; whether it is read is stored by
     * {@link #markRead}.
     */
    Notification save(
        final Notification notification
    );

    /**
     * @throws dev.graphnous.application.exception.NotFoundException when
     *                                                                there is none
     */
    Notification findById(
        final Notification.NotificationId id,
        final UserContext recipient
    );

    /**
     * Every notification of the system, its projects and their scans.
     */
    Page<Notification> findAll(
        final System.SystemId systemId,
        final UserContext recipient,
        final PageQuery pageQuery
    );

    /**
     * Every notification of the project and its scans.
     */
    Page<Notification> findAll(
        final Project.ProjectId projectId,
        final UserContext recipient,
        final PageQuery pageQuery
    );

    Page<Notification> findAll(
        final Scan.ScanId scanId,
        final UserContext recipient,
        final PageQuery pageQuery
    );

    /**
     * The notifications of the system, its projects and their scans the
     * recipient has not read.
     */
    Page<Notification> findUnread(
        final System.SystemId systemId,
        final UserContext recipient,
        final PageQuery pageQuery
    );

    /**
     * The notifications of the project and its scans the recipient has not
     * read.
     */
    Page<Notification> findUnread(
        final Project.ProjectId projectId,
        final UserContext recipient,
        final PageQuery pageQuery
    );

    Page<Notification> findUnread(
        final Scan.ScanId scanId,
        final UserContext recipient,
        final PageQuery pageQuery
    );

    /**
     * How many notifications of the system, its projects and their scans
     * the recipient has not read.
     */
    long countUnread(
        final System.SystemId systemId,
        final UserContext recipient
    );

    /**
     * Marks the notification read or unread for the recipient.
     */
    void markRead(
        final Notification.NotificationId id,
        final UserContext recipient,
        final boolean read
    );

    /**
     * Marks every notification of the system, its projects and their scans
     * read for the recipient.
     *
     * @return how many were unread for them
     */
    int markAllRead(
        final System.SystemId systemId,
        final UserContext recipient
    );

    /**
     * Deletes the notification, and who read it.
     */
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
