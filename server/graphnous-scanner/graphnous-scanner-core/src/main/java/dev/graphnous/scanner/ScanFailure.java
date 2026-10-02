package dev.graphnous.scanner;

import dev.graphnous.scanner.model.ScanTarget;

/**
 * A scan target whose scanner failed; the other targets are still scanned.
 */
public record ScanFailure(
    ScanTarget target,
    RuntimeException error
) {

    public String message() {
        return error.getMessage() == null
            ? error.getClass().getSimpleName()
            : error.getMessage();
    }
}
