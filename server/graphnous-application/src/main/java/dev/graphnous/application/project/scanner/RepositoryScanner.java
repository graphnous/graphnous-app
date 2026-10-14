package dev.graphnous.application.project.scanner;

import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.scanner.ScanReport;
import dev.graphnous.scanner.plan.ScanPlan;

import java.nio.file.Path;
import java.util.Set;

/**
 * Plans and scans the targets found below a checked out repository path.
 */
public interface RepositoryScanner {

    /**
     * Finds the targets to scan below the checked out repository path.
     */
    default ScanPlan plan(Path path, ScanLogger logger) {
        return plan(path, Set.of(), logger);
    }

    /**
     * Finds the targets to scan below the checked out repository path in
     * the given languages, and leaves the others out.
     *
     * @param languages the languages to scan; every language when empty
     */
    ScanPlan plan(Path path, Set<ScanTarget.Language> languages, ScanLogger logger);

    /**
     * Scans every target of the plan; see {@link ScanReport} for the
     * results and the targets that failed.
     *
     * @param organization the organization the scan is for, which decides
     *                     the files its scanners get, see {@link ScannerFiles}
     */
    ScanReport scan(Scan.ScanId scanId, OrganizationId organization, Path path, ScanPlan plan, ScanLogger logger);

}
