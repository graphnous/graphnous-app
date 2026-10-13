package dev.graphnous.application.scan.result;

import dev.graphnous.core.model.ScanResult;
import dev.graphnous.domain.scan.Scan;

import java.util.List;

/**
 * Keeps the results of a scan as the scanners produced them. The graph
 * holds only part of them, so enhancing a scan again needs the results
 * themselves.
 */
public interface ScanResultArchive {

    /**
     * Keeps the results of the scan, replacing the ones it kept before.
     */
    void save(Scan.ScanId scanId, List<ScanResult> results);

    /**
     * Whether results of the scan are kept; not for a scan stored before
     * results were.
     */
    boolean exists(Scan.ScanId scanId);

    /**
     * The results of the scan, in the order they were kept; none when none
     * are kept.
     */
    List<ScanResult> load(Scan.ScanId scanId);

    void delete(Scan.ScanId scanId);

}
