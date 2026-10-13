package dev.graphnous.application.project.scanner;

import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.scanner.definition.ScannerFile;

import java.util.List;

/**
 * The files to put into the containers of a scanner, such as the Maven
 * settings with the repositories and credentials the Java scanner
 * downloads libraries with. Where they come from depends on where the
 * server runs: files the server reads when it is self-hosted, a secret
 * vault per organization in the cloud.
 */
public interface ScannerFiles {

    /**
     * The files for the scanner of the language, when it scans for the
     * organization; none when the scanner needs none.
     *
     * @throws IllegalStateException when a file the scanner needs cannot be
     *                               read, which fails the target
     */
    List<ScannerFile> files(ScanTarget.Language language, OrganizationId organization);

    /**
     * No files for any scanner.
     */
    static ScannerFiles none() {
        return (language, organization) -> List.of();
    }
}
