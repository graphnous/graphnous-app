package dev.graphnous.persistence.scan.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface ScanStatJpaRepository extends JpaRepository<ScanStatEntity, UUID> {

    Optional<ScanStatEntity> findByScanId(UUID scanId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ScanStatEntity s WHERE s.scanId = :scanId")
    void deleteByScanId(
        @Param("scanId") UUID scanId
    );
}
