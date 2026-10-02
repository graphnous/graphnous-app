package dev.graphnous.scanner.docker;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Tar archives for Docker's copy API, which moves files in and out of a
 * container without mounting host directories. That keeps scans working
 * when this process runs in a container itself, where its own paths do
 * not exist on the Docker host.
 */
final class ContainerArchive {

    private static final int WRITABLE_DIRECTORY_MODE = 040777;

    private ContainerArchive() {
    }

    /**
     * An archive to extract at the container root, holding an empty
     * directory any container user can write to.
     */
    static byte[] writableDirectory(final String directory) throws IOException {
        final var bytes = new ByteArrayOutputStream();

        try (final var tar = new TarArchiveOutputStream(bytes)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

            directory(tar, relative(directory), WRITABLE_DIRECTORY_MODE);
        }

        return bytes.toByteArray();
    }

    /**
     * The content of the first file in an archive, as Docker returns a
     * single copied file.
     *
     * @throws IOException when the archive holds no file
     */
    static InputStream firstFile(final InputStream archive) throws IOException {
        final var tar = new TarArchiveInputStream(archive);

        for (var entry = tar.getNextEntry(); entry != null; entry = tar.getNextEntry()) {
            if (entry.isFile()) {
                return tar;
            }
        }

        throw new IOException("Archive contains no file");
    }

    private static void directory(
        final TarArchiveOutputStream tar,
        final String name,
        final int mode
    ) throws IOException {
        final var entry = new TarArchiveEntry(name + "/");
        entry.setMode(mode);

        tar.putArchiveEntry(entry);
        tar.closeArchiveEntry();
    }

    private static String relative(final String absolute) {
        return absolute.replaceFirst("^/+", "");
    }
}
