package dev.graphnous.persistence.scan;

import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.system.SystemEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ScanJpaRepository extends JpaRepository<ScanEntity, UUID> {

    Page<ScanEntity> findAllByProjectId(
        final UUID projectId,
        final Pageable pageable
    );


    List<ScanEntity> findAllByStatusIn(
        final Collection<Scan.ScanStatus> statuses
    );

    boolean existsByProjectIdAndStatusIn(
        final UUID projectId,
        final Collection<Scan.ScanStatus> statuses
    );

    int countByProjectId(
        final UUID projectId
    );

    @Query("SELECT s FROM ScanEntity s WHERE s.status NOT IN :active AND s.createdAt < :cutoff")
    List<ScanEntity> findAllByStatusNotInAndCreatedAtBefore(
        @Param("active") final Collection<Scan.ScanStatus> active,
        @Param("cutoff") final Instant cutoff
    );

    @Query("SELECT s.id FROM ScanEntity s WHERE s.projectId = :projectId AND s.status NOT IN :active ORDER BY s.createdAt ASC")
    List<UUID> findIdsByProjectIdAndStatusNotInOldestFirst(
        @Param("projectId") final UUID projectId,
        @Param("active") final Collection<Scan.ScanStatus> active,
        final Pageable pageable
    );

    @Modifying
    @Transactional
    @Query("UPDATE ScanEntity s SET s.revision = :revision WHERE s.id = :id")
    int updateRevision(
        @Param("id") final UUID id,
        @Param("revision") final String revision
    );

    @Query("SELECT s.id FROM ScanEntity s WHERE s.projectId = :projectId")
    List<UUID> findIdsByProjectId(
        @Param("projectId") final UUID projectId
    );
}
