package dev.graphnous.persistence.scan.archive;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface ScanResultArchiveJpaRepository extends JpaRepository<ScanResultArchiveEntity, UUID> {

    List<ScanResultArchiveEntity> findByScanIdOrderByPositionAsc(
        UUID scanId
    );

    boolean existsByScanId(
        UUID scanId
    );

    @Modifying
    @Transactional
    @Query("DELETE FROM ScanResultArchiveEntity a WHERE a.scanId = :scanId")
    void deleteByScanId(
        @Param("scanId") UUID scanId
    );
}
