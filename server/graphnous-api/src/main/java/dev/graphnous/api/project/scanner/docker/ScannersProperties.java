package dev.graphnous.api.project.scanner.docker;

import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.scanner.definition.ImageScannerDefinition;
import dev.graphnous.scanner.definition.ScannerDefinition;
import dev.graphnous.scanner.definition.ScannerFile;
import dev.graphnous.scanner.definition.ScannerMount;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The scanners, by the language of the targets they scan. Each is
 * published as an image; see {@link ImageScannerDefinition} for the
 * placeholders its command may hold.
 *
 * @param scanners the scanner for each language; a target of a language
 *                 without one fails to scan
 */
@ConfigurationProperties("graphnous")
public record ScannersProperties(
    Map<ScanTarget.Language, Scanner> scanners
) {

    public ScannersProperties {
        scanners = scanners == null ? Map.of() : Map.copyOf(scanners);
    }

    /**
     * @param image       the image the scanner runs in, such as
     *                    {@code ghcr.io/graphnous/graphnous-java-scanner:0.3.0}
     * @param command     the command running the scanner, replacing the
     *                    image's entrypoint
     * @param output      where in the container the scanner writes its result
     * @param mounts      what the container mounts besides the repository,
     *                    such as a cache the scanner keeps between scans
     * @param environment the environment variables of the container, such
     *                    as credentials the scanner needs
     * @param files       files this server reads and puts into the
     *                    container, such as settings with credentials; see
     *                    {@link ConfiguredScannerFiles}
     */
    public record Scanner(
        String image,
        List<String> command,
        String output,
        List<Mount> mounts,
        Map<String, String> environment,
        List<File> files
    ) {

        public Scanner {
            mounts = mounts == null ? List.of() : List.copyOf(mounts);
            environment = environment == null ? Map.of() : Map.copyOf(environment);
            files = files == null ? List.of() : List.copyOf(files);

            for (final var file : files) {
                if (file.source() == null) {
                    throw new IllegalArgumentException("A scanner file needs the source this server reads it from");
                }

                // Rejected at startup rather than at the first scan
                new ScannerFile(file.target(), new byte[0]);
            }
        }
    }

    /**
     * @param source where this server reads the file, e.g. a file mounted
     *               into its container
     * @param target where the scanner's container sees it
     */
    public record File(
        Path source,
        String target
    ) {
    }

    /**
     * @param type      {@code volume}: the named Docker volume
     *                  {@code source}, created on first use. {@code bind}:
     *                  the directory or file {@code source} on the Docker
     *                  host.
     * @param source    the volume name, or the absolute path on the Docker host
     * @param target    the absolute path in the container
     * @param readOnly  whether the scanner may only read it
     */
    public record Mount(
        ScannerMount.Type type,
        String source,
        String target,
        boolean readOnly
    ) {
    }

    public List<ScannerDefinition> definitions() {
        return scanners.entrySet().stream()
            .<ScannerDefinition>map(entry -> new ImageScannerDefinition(
                entry.getKey(),
                entry.getValue().image(),
                entry.getValue().command(),
                entry.getValue().output(),
                entry.getValue().mounts()
                    .stream()
                    .map(mount -> new ScannerMount(mount.type(), mount.source(), mount.target(), mount.readOnly()))
                    .toList(),
                entry.getValue().environment()
            ))
            .toList();
    }
}
