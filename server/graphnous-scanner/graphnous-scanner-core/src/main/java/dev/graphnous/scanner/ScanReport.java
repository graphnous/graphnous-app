package dev.graphnous.scanner;

import dev.graphnous.core.model.ScanResult;

import java.util.List;

/**
 * The results of the targets that were scanned, and the targets that failed.
 */
public record ScanReport(
    List<ScanResult> results,
    List<ScanFailure> failures
) {

    public ScanReport {
        results = List.copyOf(results);
        failures = List.copyOf(failures);
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }
}
