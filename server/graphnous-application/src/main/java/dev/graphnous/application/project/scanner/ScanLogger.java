package dev.graphnous.application.project.scanner;

import dev.graphnous.domain.scan.log.ScanLog;

/**
 * Writes to the log of the scan that is running.
 */
@FunctionalInterface
public interface ScanLogger {

    void log(ScanLog.ScanLogLevel level, String message);

}
