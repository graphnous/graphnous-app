package dev.graphnous.persistence.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRecipientJpaRepository extends JpaRepository<NotificationRecipientEntity, UUID> {

    /**
     * @param user the user's id, or {@link NotificationRepositoryImpl#NO_USER}
     *             for no user
     */
    @Query("""
        SELECT r FROM NotificationRecipientEntity r
        WHERE r.notificationId = :notificationId AND COALESCE(r.userId, :noUser) = :user
        """)
    Optional<NotificationRecipientEntity> findRecipient(
        @Param("notificationId") final UUID notificationId,
        @Param("user") final UUID user,
        @Param("noUser") final UUID noUser
    );

    /**
     * Marks every notification of the system read for the user, adding
     * the user as a recipient of those they never marked.
     *
     * @return how many were unread for them
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        INSERT INTO notification_recipients (id, notification_id, user_id, is_read, read_at)
        SELECT gen_random_uuid(), n.id, :userId, TRUE, CURRENT_TIMESTAMP
        FROM notifications n
        WHERE n.system_id = :systemId
        ON CONFLICT (notification_id, user_id) DO UPDATE SET is_read = TRUE, read_at = CURRENT_TIMESTAMP
        WHERE notification_recipients.is_read = FALSE
        """)
    int markAllRead(
        @Param("systemId") final UUID systemId,
        @Param("userId") final UUID userId
    );

    /**
     * As {@link #markAllRead}, for no user, as without security.
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        INSERT INTO notification_recipients (id, notification_id, user_id, is_read, read_at)
        SELECT gen_random_uuid(), n.id, NULL, TRUE, CURRENT_TIMESTAMP
        FROM notifications n
        WHERE n.system_id = :systemId
        ON CONFLICT (notification_id, user_id) DO UPDATE SET is_read = TRUE, read_at = CURRENT_TIMESTAMP
        WHERE notification_recipients.is_read = FALSE
        """)
    int markAllReadWithoutUser(
        @Param("systemId") final UUID systemId
    );
}
