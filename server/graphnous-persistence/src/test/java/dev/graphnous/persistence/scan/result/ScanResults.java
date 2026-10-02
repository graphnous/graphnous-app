package dev.graphnous.persistence.scan.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.scanner.model.ScanResultSchema;

import java.io.IOException;
import java.io.UncheckedIOException;

final class ScanResults {

    private ScanResults() {
    }

    /**
     * One target with a module of three classes: Order extends Entity and
     * implements Identified and the external Comparable.
     */
    static ScanResultSchema orders() {
        try (final var json = ScanResults.class.getResourceAsStream("/scan-result.json")) {
            return new ObjectMapper().readValue(json, ScanResultSchema.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
