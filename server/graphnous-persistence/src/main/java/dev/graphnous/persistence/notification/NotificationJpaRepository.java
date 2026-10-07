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
