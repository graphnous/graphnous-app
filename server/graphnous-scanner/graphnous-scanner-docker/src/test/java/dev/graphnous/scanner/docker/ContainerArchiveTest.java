package dev.graphnous.scanner.docker;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContainerArchiveTest {

    @Test
    void holdsAWritableDirectory() throws IOException {
        final var entries = entries(ContainerArchive.writableDirectory("/output"));

        assertThat(entries.keySet()).containsExactly("output/");
        assertThat(entries.get("output/").isDirectory()).isTrue();
        assertThat(entries.get("output/").getMode() & 0777).isEqualTo(0777);
    }

    @Test
    void holdsANestedDirectory() throws IOException {
        assertThat(entries(ContainerArchive.writableDirectory("/tmp/graphnous/output")).keySet())
            .containsExactly("tmp/graphnous/output/");
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
