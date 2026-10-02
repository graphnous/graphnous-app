package dev.graphnous.application.scan.result;

import dev.graphnous.domain.scan.Scan;
import dev.graphnous.scanner.model.ScanResultSchema;

import java.util.List;

/**
 * Stores what a scan found as a graph below the scan.
 */
public interface ScanResultRepository {

    /**
     * Replaces the stored results of the scan with these.
     */
    void save(Scan.ScanId scanId, List<ScanResultSchema> results);

    /**
     * Deletes the stored results of the scan, and the dependencies no other
     * scan uses.
     */
    void delete(Scan.ScanId scanId);

}
