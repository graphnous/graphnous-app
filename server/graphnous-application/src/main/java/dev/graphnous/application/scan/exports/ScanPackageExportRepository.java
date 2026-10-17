package dev.graphnous.application.scan.exports;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.scan.Scan;

/**
 * Reads what a scan uses of packages from its graph.
 */
public interface ScanPackageExportRepository {

    /**
     * A page of the scan's package exports that pass the filter, sorted by
     * one of {@link ScanPackageExportService#SORTS}, then by id.
     */
    Page<ScanPackageExport> findPackageExports(Scan.ScanId scanId, ScanPackageExportFilter filter, PageQuery page);
}
