package dev.graphnous.persistence.scan.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.core.model.ScanResult;

import java.io.IOException;
import java.io.UncheckedIOException;

final class ScanResults {

    private ScanResults() {
    }

    /**
     * One target with a module of three classes: Order extends Entity and
     * implements Identified and the external Comparable.
     */
    static ScanResult orders() {
        try (final var json = ScanResults.class.getResourceAsStream("/scan-result.json")) {
            return new ObjectMapper().readValue(json, ScanResult.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
