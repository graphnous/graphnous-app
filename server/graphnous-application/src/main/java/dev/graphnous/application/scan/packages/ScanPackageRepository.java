package dev.graphnous.application.scan.packages;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.scan.Scan;

/**
 * Reads the packages a scan found from its graph.
 */
public interface ScanPackageRepository {

    /**
     * A page of the scan's packages that pass the filter, sorted by one of
     * {@link ScanPackageService#SORTS}, then by id.
     */
    Page<ScanPackage> findPackages(Scan.ScanId scanId, ScanPackageFilter filter, PageQuery page);
}
