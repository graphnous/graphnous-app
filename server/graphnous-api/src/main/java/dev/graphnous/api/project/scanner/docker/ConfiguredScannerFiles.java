package dev.graphnous.api.project.scanner.docker;

import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.project.scanner.ScannerFiles;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.scanner.definition.ScannerFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The files of the scanners as {@code graphnous.scanners.<LANGUAGE>.files}
 * configures them, read by this server from its own filesystem for each
 * scan, so a changed file applies to the next scan. They are the same for
 * every organization, as a self-hosted server has one.
 */
public class ConfiguredScannerFiles implements ScannerFiles {

    private final Map<ScanTarget.Language, List<ScannersProperties.File>> files;

    public ConfiguredScannerFiles(final ScannersProperties properties) {
        this.files = properties.scanners()
            .entrySet()
            .stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().files()));
    }

    @Override
    public List<ScannerFile> files(
        final ScanTarget.Language language,
        final OrganizationId organization
    ) {
        return files.getOrDefault(language, List.of())
            .stream()
            .map(file -> {
                try {
                    return new ScannerFile(file.target(), Files.readAllBytes(file.source()));
                } catch (IOException e) {
                    throw new IllegalStateException(
                        "Could not read " + file.source() + " for the " + language + " scanner's " + file.target(),
                        e
                    );
                }
            })
            .toList();
    }
}
