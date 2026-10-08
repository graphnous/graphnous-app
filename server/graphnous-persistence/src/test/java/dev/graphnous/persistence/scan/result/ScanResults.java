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
        return read("/scan-result.json");
    }

    /**
     * A Python target with functions and variables outside any class: the
     * file app/orders.py in package app declares the class Order, the
     * function total with a decorator, and the variable TAX_RATE; main.py,
     * in no package, the function main.
     */
    static ScanResult shop() {
        return read("/scan-result-python.json");
    }

    private static ScanResult read(final String resource) {
        try (final var json = ScanResults.class.getResourceAsStream(resource)) {
            return new ObjectMapper().readValue(json, ScanResult.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
