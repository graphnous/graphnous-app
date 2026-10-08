package dev.graphnous.application.project.scanner;

import dev.graphnous.domain.scan.Scan;

import java.nio.file.Path;

/**
 * Makes the source of a scan available where the {@link RepositoryScanner}
 * can read it.
 */
public interface SourceCheckout {

    /**
     * Checks out the repository at the revision the scan was asked for.
     *
     * @param revision the source to check out: its requested revision, or
     *                 the tip of its branch when it has none, or of the
     *                 default branch when it has neither; {@code null} for
     *                 the tip of the default branch
     */
    CheckedOut checkout(
        Scan.ScanId scanId,
        String gitUrl,
        Scan.SourceRevision revision,
        ScanLogger logger
    );

    void remove(Scan.ScanId scanId, ScanLogger logger);

    /**
     * @param path     where the checkout is
     * @param revision the commit checked out, as its full hash
     */
    record CheckedOut(Path path, String revision) {
    }

}
