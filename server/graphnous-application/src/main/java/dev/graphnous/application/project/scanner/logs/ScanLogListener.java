package dev.graphnous.application.project.scanner.logs;

import dev.graphnous.domain.scan.log.ScanLog;

public interface ScanLogListener {

    void onLog(ScanLog log);

}
