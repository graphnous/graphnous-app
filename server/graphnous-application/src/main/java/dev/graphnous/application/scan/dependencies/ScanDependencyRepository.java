package dev.graphnous.application.scan.dependencies;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.scan.Scan;

/**
 * Reads what the modules of a scan depend on from its graph.
 */
public interface ScanDependencyRepository {

    /**
     * A page of the dependencies of the scan's modules that pass the
     * filter, one per module and library, sorted by one of
     * {@link ScanDependencyService#SORTS}, then by module and name.
     */
    Page<ScanDependency> findDependencies(Scan.ScanId scanId, ScanDependencyFilter filter, PageQuery page);
}
