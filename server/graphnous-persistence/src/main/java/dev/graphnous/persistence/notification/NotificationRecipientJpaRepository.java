package dev.graphnous.persistence.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRecipientJpaRepository extends JpaRepository<NotificationRecipientEntity, UUID> {

    Optional<NotificationRecipientEntity> findByNotificationIdAndUserId(
        final UUID notificationId,
        final UUID userId
    );

    /**
     * The row of no user, as without security.
     */
    Optional<NotificationRecipientEntity> findByNotificationIdAndUserIdIsNull(
        final UUID notificationId
    );

    List<NotificationRecipientEntity> findAllByNotificationIdInAndUserId(
        final Collection<UUID> notificationIds,
        final UUID userId
    );

    List<NotificationRecipientEntity> findAllByNotificationIdInAndUserIdIsNull(
        final Collection<UUID> notificationIds
    );
}
