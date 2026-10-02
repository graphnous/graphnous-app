package dev.graphnous.scanner.definition;

import dev.graphnous.scanner.model.ScanTarget;

import java.nio.file.Path;
import java.util.List;

public interface ScannerDefinition {

    boolean supports(ScanTarget target);

    /**
     * The scanner artifact (jar or script) on this machine.
     */
    Path scanner();

    /**
     * The command running the scanner directly on this machine.
     */
    List<String> command(Path repository, ScanTarget target);

    /**
     * The command running the scanner inside {@link #image(ScanTarget)},
     * with the scanner artifact and repository mounted at the given
     * container paths.
     */
    List<String> containerCommand(String scanner, String repository, ScanTarget target);

    /**
     * The image providing the runtime the scanner needs.
     */
    String image(ScanTarget target);

}
