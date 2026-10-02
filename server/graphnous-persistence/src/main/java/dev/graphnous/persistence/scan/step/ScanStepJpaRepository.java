package dev.graphnous.persistence.scan.step;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface ScanStepJpaRepository extends JpaRepository<ScanStepEntity, UUID> {

    List<ScanStepEntity> findByScanIdOrderByPositionAsc(UUID scanId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ScanStepEntity s WHERE s.scanId = :scanId")
    void deleteByScanId(
        @Param("scanId") UUID scanId
    );
}
