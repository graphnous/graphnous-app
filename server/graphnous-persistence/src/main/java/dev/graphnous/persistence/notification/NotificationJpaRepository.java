package dev.graphnous.persistence.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface NotificationJpaRepository
    extends JpaRepository<NotificationEntity, UUID>, JpaSpecificationExecutor<NotificationEntity> {

    Page<NotificationEntity> findAllBySystemId(
        final UUID systemId,
        final Pageable pageable
    );

    Page<NotificationEntity> findAllByProjectId(
        final UUID projectId,
        final Pageable pageable
    );

    Page<NotificationEntity> findAllByScanId(
        final UUID scanId,
        final Pageable pageable
    );

    // Their recipients go with them, as the database cascades

    @Transactional
    void deleteBySystemId(
        final UUID systemId
    );

    @Transactional
    void deleteByProjectId(
        final UUID projectId
    );

    @Transactional
    void deleteByScanId(
        final UUID scanId
    );

    static Specification<NotificationEntity> ofSystem(final UUID systemId) {
        return (root, query, builder) -> builder.equal(root.get("systemId"), systemId);
    }

    static Specification<NotificationEntity> ofProject(final UUID projectId) {
        return (root, query, builder) -> builder.equal(root.get("projectId"), projectId);
    }

    static Specification<NotificationEntity> ofScan(final UUID scanId) {
        return (root, query, builder) -> builder.equal(root.get("scanId"), scanId);
    }

    /**
     * Those the user did not mark read, no user being null: without a
     * recipient row of theirs that says read.
     */
    static Specification<NotificationEntity> unreadBy(final UUID userId) {
        return (root, query, builder) -> {
            final var read = query.subquery(Integer.class);
            final var recipient = read.from(NotificationRecipientEntity.class);

            read.select(builder.literal(1)).where(
                builder.equal(recipient.get("notificationId"), root.get("id")),
                builder.isTrue(recipient.get("read")),
                userId == null ? builder.isNull(recipient.get("userId")) : builder.equal(recipient.get("userId"), userId)
            );

            return builder.not(builder.exists(read));
        };
    }
}
