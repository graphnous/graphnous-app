package dev.graphnous.scanner.definition;

import dev.graphnous.scanner.model.ScanTarget;

import java.util.List;

/**
 * A scanner published as an image: the image holds the scanner and its
 * runtime, and the command runs it with the repository mounted in the
 * container.
 */
public interface ScannerDefinition {

    boolean supports(ScanTarget target);

    /**
     * The image the scanner runs in, such as
     * {@code ghcr.io/graphnous/graphnous-java-scanner:0.1.0}.
     */
    String image();

    /**
     * The command running the scanner in its image, with the repository
     * mounted at the given container path. It writes the result to
     * {@link #output()}.
     */
    List<String> command(String repository, ScanTarget target);

    /**
     * Where in the container the scanner writes its result.
     */
    String output();

}
