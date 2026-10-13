package dev.graphnous.application.scan.dependencies;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.scan.Scan;

import java.util.List;

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

    /**
     * A page of the dependencies of the modules of every one of the scans
     * that pass the filter, one per scan, module and library, sorted as
     * those of one scan are.
     */
    Page<SystemDependency> findDependencies(List<Scan> scans, ScanDependencyFilter filter, PageQuery page);

    /**
     * A page of the libraries the modules of the scans depend on, each once
     * with every version in use and the projects of the scans that use it,
     * sorted by one of {@link ScanDependencyService#LIBRARY_SORTS}, then by
     * name.
     *
     * @param search    part of the library's name, ignoring case; null for
     *                  every library
     * @param driftOnly only the libraries used in more than one version
     */
    Page<SystemLibrary> findLibraries(List<Scan> scans, String search, boolean driftOnly, PageQuery page);
}
