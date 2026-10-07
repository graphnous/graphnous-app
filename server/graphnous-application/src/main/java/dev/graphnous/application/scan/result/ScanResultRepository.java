package dev.graphnous.application.scan.result;

import dev.graphnous.core.model.ScanResult;
import dev.graphnous.domain.scan.Scan;

import java.util.List;

/**
 * Stores what a scan found as a graph below the scan.
 */
public interface ScanResultRepository {

    /**
     * Replaces the stored results of the scan with these.
     */
    void save(Scan.ScanId scanId, List<ScanResult> results);

    /**
     * Deletes the stored results of the scan, and the dependencies no other
     * scan uses.
     */
    void delete(Scan.ScanId scanId);

}
