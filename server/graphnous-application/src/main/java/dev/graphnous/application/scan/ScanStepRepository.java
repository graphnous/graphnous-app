package dev.graphnous.application.scan;

import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.ScanStep;

import java.util.List;

public interface ScanStepRepository {

    void saveAll(List<ScanStep> steps);

    ScanStep save(ScanStep step);

    /**
     * The scan's steps, in the order they run.
     */
    List<ScanStep> findByScanId(Scan.ScanId scanId);

    void deleteByScanId(Scan.ScanId scanId);

}
