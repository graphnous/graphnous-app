package dev.graphnous.persistence.scan.log;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanLogJpaRepository extends JpaRepository<ScanLogEntity, UUID> {

    List<ScanLogEntity> findByScanIdOrderBySequenceAsc(
        UUID scanId
    );

    List<ScanLogEntity> findByScanIdAndSequenceGreaterThanOrderBySequenceAsc(
        UUID scanId,
        long sequence
    );

    @Query("""
        SELECT MAX(s.sequence)
        FROM ScanLogEntity s
        WHERE s.scanId = :scanId
        """)
    Optional<Long> findLatestSequence(
        @Param("scanId") UUID scanId
    );

    @Modifying
    @Transactional
    @Query("DELETE FROM ScanLogEntity s WHERE s.scanId = :scanId")
    void deleteByScanId(
        @Param("scanId") UUID scanId
    );
}
