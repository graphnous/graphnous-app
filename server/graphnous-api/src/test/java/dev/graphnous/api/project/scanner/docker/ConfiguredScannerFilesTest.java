package dev.graphnous.api.project.scanner.docker;

import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.scanner.definition.ScannerFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class ConfiguredScannerFilesTest {

    private static final OrganizationId ORGANIZATION = new OrganizationId(UUID.randomUUID());

    @TempDir
    Path directory;

    @Test
    void readsTheFilesOfTheScannerForEachScan() throws IOException {
        final var settings = Files.writeString(directory.resolve("maven-settings.xml"), "<settings/>");
        final var files = files(settings);

        assertThat(files.files(ScanTarget.Language.JAVA, ORGANIZATION))
            .extracting(ScannerFile::target, file -> new String(file.content(), StandardCharsets.UTF_8))
            .containsExactly(tuple("/maven-settings/settings.xml", "<settings/>"));

        // A changed file applies to the next scan
        Files.writeString(settings, "<settings><mirrors/></settings>");

        assertThat(files.files(ScanTarget.Language.JAVA, ORGANIZATION))
            .extracting(file -> new String(file.content(), StandardCharsets.UTF_8))
            .containsExactly("<settings><mirrors/></settings>");
    }

    @Test
    void givesOtherScannersNone() {
        assertThat(files(directory.resolve("maven-settings.xml")).files(ScanTarget.Language.TYPESCRIPT, ORGANIZATION))
            .isEmpty();
    }

    @Test
    void failsTheScanWhenAFileCannotBeRead() {
        final var missing = directory.resolve("missing.xml");

        assertThatThrownBy(() -> files(missing).files(ScanTarget.Language.JAVA, ORGANIZATION))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Could not read " + missing + " for the JAVA scanner's /maven-settings/settings.xml");
    }

    private static ConfiguredScannerFiles files(final Path settings) {
        return new ConfiguredScannerFiles(new ScannersProperties(Map.of(
            ScanTarget.Language.JAVA,
            new ScannersProperties.Scanner(
                "scanner:1", List.of("scan"), "/output/scan-result.json",
                List.of(), Map.of(),
                List.of(new ScannersProperties.File(settings, "/maven-settings/settings.xml"))
            ),
            ScanTarget.Language.TYPESCRIPT,
            new ScannersProperties.Scanner("scanner:2", List.of("scan"), "/output/scan-result.json", null, null, null)
        )));
    }
}
