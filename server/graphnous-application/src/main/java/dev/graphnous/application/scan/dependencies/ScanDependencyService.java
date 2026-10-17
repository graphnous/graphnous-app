package dev.graphnous.application.scan.dependencies;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.ScanListing;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;

import java.util.Set;

/**
 * Lists what the modules of a scan depend on, for a user who may read the
 * scan: getting the scan first checks the user's permission and
 * organization.
 */
public class ScanDependencyService {

    /**
     * The properties dependencies are sorted by.
     */
    public static final Set<String> SORTS = Set.of("name", "module", "scope");

    /**
     * The properties the libraries of a system are sorted by: their name,
     * or how many versions of them are in use.
     */
    public static final Set<String> LIBRARY_SORTS = Set.of("name", "versions");

    private final ScanService scanService;
    private final ScanDependencyRepository scanDependencyRepository;

    public ScanDependencyService(
        final ScanService scanService,
        final ScanDependencyRepository scanDependencyRepository
    ) {
        this.scanService = scanService;
        this.scanDependencyRepository = scanDependencyRepository;
    }

    public Page<ScanDependency> getDependencies(
        final RequestContext context,
        final Scan.ScanId scanId,
        final ScanDependencyFilter filter,
        final PageQuery page
    ) {
        scanService.getScan(context, scanId);

        return scanDependencyRepository.findDependencies(scanId, filter, ScanListing.sorted(page, SORTS));
    }

    /**
     * What the modules of a system depend on: the dependencies of the
     * latest completed scan of each of its projects. Getting those scans
     * checks the user's permission and organization.
     */
    public Page<SystemDependency> getSystemDependencies(
        final RequestContext context,
        final System.SystemId systemId,
        final ScanDependencyFilter filter,
        final PageQuery page
    ) {
        final var sorted = ScanListing.sorted(page, SORTS);

        return scanDependencyRepository.findDependencies(
            scanService.getNewestCompletedScans(context, systemId),
            filter,
            sorted
        );
    }

    /**
     * The libraries a system depends on, each with the versions its
     * projects use: those of the latest completed scan of each project.
     * Getting those scans checks the user's permission and organization.
     *
     * @param search    part of the library's name, ignoring case
     * @param driftOnly only the libraries used in more than one version
     */
    public Page<SystemLibrary> getSystemLibraries(
        final RequestContext context,
        final System.SystemId systemId,
        final String search,
        final boolean driftOnly,
        final PageQuery page
    ) {
        final var sorted = ScanListing.sorted(page, LIBRARY_SORTS);

        return scanDependencyRepository.findLibraries(
            scanService.getNewestCompletedScans(context, systemId),
            ScanListing.filter(search),
            driftOnly,
            sorted
        );
    }
}
