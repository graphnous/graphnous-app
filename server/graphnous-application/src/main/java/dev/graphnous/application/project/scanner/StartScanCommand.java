package dev.graphnous.application.project.scanner;

import dev.graphnous.domain.scan.Scan;

import java.util.Set;

/**
 * @param enhancers the names of the enhancers to run on the scan's results;
 *                  null for every installed enhancer, empty for none
 */
public record StartScanCommand(
    Scan.ScanId scanId,
    Set<String> enhancers
) {

    /**
     * A scan that every installed enhancer runs on.
     */
    public StartScanCommand(final Scan.ScanId scanId) {
        this(scanId, null);
    }
}
