package dev.graphnous.persistence.scan.stats;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanStatJpaRepository extends JpaRepository<ScanStatEntity, UUID> {

    Optional<ScanStatEntity> findByScanId(UUID scanId);

    /**
     * The project's stats, of its most recently created scan first.
     */
    @Query("""
        SELECT stats FROM ScanStatEntity stats, ScanEntity scan
        WHERE scan.id = stats.scanId AND stats.projectId = :projectId
        ORDER BY scan.createdAt DESC
        """)
    List<ScanStatEntity> findLatestByProjectId(
        @Param("projectId") UUID projectId,
        Pageable pageable
    );

    /**
     * The stats of the most recently created scan that has any, of each
     * project of the system.
     */
    @Query("""
        SELECT stats FROM ScanStatEntity stats, ScanEntity scan, ProjectEntity project
        WHERE scan.id = stats.scanId
          AND project.id = stats.projectId
          AND project.systemId = :systemId
          AND scan.createdAt = (
            SELECT MAX(otherScan.createdAt) FROM ScanStatEntity other, ScanEntity otherScan
            WHERE otherScan.id = other.scanId AND other.projectId = stats.projectId
          )
        """)
    List<ScanStatEntity> findLatestBySystemId(
        @Param("systemId") UUID systemId
    );

    @Modifying
    @Transactional
    @Query("DELETE FROM ScanStatEntity s WHERE s.scanId = :scanId")
    void deleteByScanId(
        @Param("scanId") UUID scanId
    );
}
