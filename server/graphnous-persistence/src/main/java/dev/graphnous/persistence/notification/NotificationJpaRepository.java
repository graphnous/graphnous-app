package dev.graphnous.persistence.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {

    /**
     * The notifications, each with its recipient's row: the user's, or that
     * of no user, matched as {@code COALESCE(r.userId, :noUser) = :user}, as
     * no user has no id to compare.
     */
    String ROWS = """
        SELECT new dev.graphnous.persistence.notification.NotificationRow(n, r.read)
        FROM NotificationEntity n
        LEFT JOIN NotificationRecipientEntity r ON r.notificationId = n.id AND COALESCE(r.userId, :noUser) = :user
        """;

    String COUNT = """
        SELECT COUNT(n)
        FROM NotificationEntity n
        LEFT JOIN NotificationRecipientEntity r ON r.notificationId = n.id AND COALESCE(r.userId, :noUser) = :user
        """;

    String UNREAD = " AND COALESCE(r.read, FALSE) = FALSE";

    @Query(value = ROWS + "WHERE n.id = :id")
    java.util.Optional<NotificationRow> findRow(
        @Param("id") final UUID id,
        @Param("user") final UUID user,
        @Param("noUser") final UUID noUser
    );

    @Query(value = ROWS + "WHERE n.systemId = :systemId", countQuery = COUNT + "WHERE n.systemId = :systemId")
    Page<NotificationRow> findRowsOfSystem(
        @Param("systemId") final UUID systemId,
        @Param("user") final UUID user,
        @Param("noUser") final UUID noUser,
        final Pageable pageable
    );

    @Query(value = ROWS + "WHERE n.projectId = :projectId", countQuery = COUNT + "WHERE n.projectId = :projectId")
    Page<NotificationRow> findRowsOfProject(
        @Param("projectId") final UUID projectId,
        @Param("user") final UUID user,
        @Param("noUser") final UUID noUser,
        final Pageable pageable
    );

    @Query(value = ROWS + "WHERE n.scanId = :scanId", countQuery = COUNT + "WHERE n.scanId = :scanId")
    Page<NotificationRow> findRowsOfScan(
        @Param("scanId") final UUID scanId,
        @Param("user") final UUID user,
        @Param("noUser") final UUID noUser,
        final Pageable pageable
    );

    @Query(value = ROWS + "WHERE n.systemId = :systemId" + UNREAD, countQuery = COUNT + "WHERE n.systemId = :systemId" + UNREAD)
    Page<NotificationRow> findUnreadOfSystem(
        @Param("systemId") final UUID systemId,
        @Param("user") final UUID user,
        @Param("noUser") final UUID noUser,
        final Pageable pageable
    );

    @Query(value = ROWS + "WHERE n.projectId = :projectId" + UNREAD, countQuery = COUNT + "WHERE n.projectId = :projectId" + UNREAD)
    Page<NotificationRow> findUnreadOfProject(
        @Param("projectId") final UUID projectId,
        @Param("user") final UUID user,
        @Param("noUser") final UUID noUser,
        final Pageable pageable
    );

    @Query(value = ROWS + "WHERE n.scanId = :scanId" + UNREAD, countQuery = COUNT + "WHERE n.scanId = :scanId" + UNREAD)
    Page<NotificationRow> findUnreadOfScan(
        @Param("scanId") final UUID scanId,
        @Param("user") final UUID user,
        @Param("noUser") final UUID noUser,
        final Pageable pageable
    );

    @Query(COUNT + "WHERE n.systemId = :systemId" + UNREAD)
    long countUnreadOfSystem(
        @Param("systemId") final UUID systemId,
        @Param("user") final UUID user,
        @Param("noUser") final UUID noUser
    );

    @Modifying
    @Transactional
    @Query("DELETE FROM NotificationEntity n WHERE n.systemId = :systemId")
    void deleteBySystemId(
        @Param("systemId") final UUID systemId
    );

    @Modifying
    @Transactional
    @Query("DELETE FROM NotificationEntity n WHERE n.projectId = :projectId")
    void deleteByProjectId(
        @Param("projectId") final UUID projectId
    );

    @Modifying
    @Transactional
    @Query("DELETE FROM NotificationEntity n WHERE n.scanId = :scanId")
    void deleteByScanId(
        @Param("scanId") final UUID scanId
    );
}
