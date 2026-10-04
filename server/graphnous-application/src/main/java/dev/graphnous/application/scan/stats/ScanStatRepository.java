package dev.graphnous.application.scan.stats;

import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.stats.ScanStats;

import java.util.Optional;

public interface ScanStatRepository {

    ScanStats save(ScanStats stats);

    Optional<ScanStats> findByScanId(Scan.ScanId scanId);

    void deleteByScanId(Scan.ScanId scanId);

}
