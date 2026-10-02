package dev.graphnous.scanner.docker;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContainerArchiveTest {

    @TempDir
    Path directory;

    @Test
    void placesTheScannerAndAWritableOutputDirectory() throws IOException {
        final var scanner = Files.writeString(directory.resolve("java-scanner.jar"), "jar-content");

        final var entries = entries(ContainerArchive.scannerAndOutput(scanner, "/scanner", "/output"));

        assertThat(entries.keySet()).containsExactly(
            "scanner/",
            "output/",
            "scanner/java-scanner.jar"
        );

        assertThat(entries.get("scanner/").isDirectory()).isTrue();
        assertThat(entries.get("output/").isDirectory()).isTrue();
        assertThat(entries.get("output/").getMode() & 0777).isEqualTo(0777);

        final var file = entries.get("scanner/java-scanner.jar");
        assertThat(file.isFile()).isTrue();
        assertThat(file.getSize()).isEqualTo("jar-content".length());
        assertThat(file.getMode() & 0777).isEqualTo(0644);
    }

    @Test
    void storesTheScannerContent() throws IOException {
        final var scanner = Files.writeString(directory.resolve("scanner.js"), "console.log('hi')");

        final var archive = ContainerArchive.scannerAndOutput(scanner, "/scanner", "/output");

        try (final var tar = new TarArchiveInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = tar.getNextEntry(); entry != null; entry = tar.getNextEntry()) {
                if (entry.isFile()) {
                    assertThat(new String(tar.readAllBytes(), StandardCharsets.UTF_8))
                        .isEqualTo("console.log('hi')");
                    return;
                }
            }
        }

        throw new AssertionError("No file in archive");
    }

    @Test
    void readsTheFirstFileOfAnArchive() throws IOException {
        final var archive = archive(Map.of("scan-result.json", "{\"format\":\"x\"}"));

        try (final var file = ContainerArchive.firstFile(new ByteArrayInputStream(archive))) {
            assertThat(new String(file.readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("{\"format\":\"x\"}");
        }
    }

    @Test
    void failsForAnArchiveWithoutFiles() {
        final var archive = archive(Map.of());

        assertThatThrownBy(() -> ContainerArchive.firstFile(new ByteArrayInputStream(archive)))
            .isInstanceOf(IOException.class)
            .hasMessage("Archive contains no file");
    }

    private static Map<String, TarArchiveEntry> entries(final byte[] archive) throws IOException {
        final var entries = new LinkedHashMap<String, TarArchiveEntry>();

        try (final var tar = new TarArchiveInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = tar.getNextEntry(); entry != null; entry = tar.getNextEntry()) {
                entries.put(entry.getName(), entry);
            }
        }

        return entries;
    }

    private static byte[] archive(final Map<String, String> files) {
        final var bytes = new ByteArrayOutputStream();

        try (final var tar = new TarArchiveOutputStream(bytes)) {
            for (final var file : files.entrySet()) {
                final var content = file.getValue().getBytes(StandardCharsets.UTF_8);
                final var entry = new TarArchiveEntry(file.getKey());
                entry.setSize(content.length);

                tar.putArchiveEntry(entry);
                tar.write(content);
                tar.closeArchiveEntry();
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        return bytes.toByteArray();
    }
}
