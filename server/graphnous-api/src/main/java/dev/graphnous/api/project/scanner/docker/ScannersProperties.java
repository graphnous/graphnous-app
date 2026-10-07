package dev.graphnous.api.project.scanner.docker;

import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.scanner.definition.ImageScannerDefinition;
import dev.graphnous.scanner.definition.ScannerDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
     * @param image   the image the scanner runs in, such as
     *                {@code ghcr.io/graphnous/graphnous-java-scanner:0.1.0}
     * @param command the command running the scanner, replacing the image's
     *                entrypoint
     * @param output  where in the container the scanner writes its result
     */
    public record Scanner(
        String image,
        List<String> command,
        String output
    ) {
    }

    public List<ScannerDefinition> definitions() {
        return scanners.entrySet().stream()
            .<ScannerDefinition>map(entry -> new ImageScannerDefinition(
                entry.getKey(),
                entry.getValue().image(),
                entry.getValue().command(),
                entry.getValue().output()
            ))
            .toList();
    }
}
