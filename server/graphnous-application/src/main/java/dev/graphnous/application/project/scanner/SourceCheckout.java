package dev.graphnous.application.project.scanner;

import dev.graphnous.domain.scan.Scan;

import java.nio.file.Path;

/**
 * Makes the source of a scan available where the {@link RepositoryScanner}
 * can read it.
 */
public interface SourceCheckout {

    /**
     * Checks out the repository at the scan's revision and returns the path
     * of the checkout.
     *
     * @param revision the revision to check out, or {@code null} for the tip
     *                 of the default branch
     */
    Path checkout(
        Scan.ScanId scanId,
        String gitUrl,
        Scan.SourceRevision revision,
        ScanLogger logger
    );

    void remove(Scan.ScanId scanId, ScanLogger logger);

}
