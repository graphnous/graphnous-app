package dev.graphnous.application.project.scanner;

import dev.graphnous.domain.scan.Scan;

/**
 * Stops the work the server started for scans, such as the containers that
 * check out and scan their repositories.
 */
public interface ScanCanceller {

    /**
     * Stops what still runs for the scan, and keeps it from starting more.
     */
    void cancel(Scan.ScanId scanId);

    /**
     * Stops everything started for any scan; for startup, when all of it
     * belongs to a run of the server that is gone.
     */
    void cancelAll();

}
