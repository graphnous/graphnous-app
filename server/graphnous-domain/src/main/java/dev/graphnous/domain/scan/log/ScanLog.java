package dev.graphnous.domain.scan.log;

import dev.graphnous.domain.scan.Scan;

import java.time.Instant;
import java.util.UUID;

public record ScanLog(
    ScanLogId id,
    Scan.ScanId scanId,
    long sequence,
    Instant timestamp,
    ScanLogLevel level,
    String message
) {

    public record ScanLogId(UUID id) {
        public static ScanLog.ScanLogId generate() {
            return new ScanLog.ScanLogId(UUID.randomUUID());
        }
    }

    public enum ScanLogLevel {
        INFO,
        WARN,
        ERROR
    }
}