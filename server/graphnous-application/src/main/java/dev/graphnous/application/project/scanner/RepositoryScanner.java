package dev.graphnous.application.project.scanner;

import dev.graphnous.domain.scan.Scan;
import dev.graphnous.scanner.ScanReport;
import dev.graphnous.scanner.plan.ScanPlan;

import java.nio.file.Path;

/**
 * Plans and scans the targets found below a checked out repository path.
 */
public interface RepositoryScanner {

    /**
     * Finds the targets to scan below the checked out repository path.
     */
    ScanPlan plan(Path path, ScanLogger logger);

    /**
     * Scans every target of the plan; see {@link ScanReport} for the
     * results and the targets that failed.
     */
    ScanReport scan(Scan.ScanId scanId, Path path, ScanPlan plan, ScanLogger logger);

}
