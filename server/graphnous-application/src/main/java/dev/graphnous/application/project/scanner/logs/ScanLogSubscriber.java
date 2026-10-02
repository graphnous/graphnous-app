package dev.graphnous.application.project.scanner.logs;

import dev.graphnous.domain.scan.Scan;

public interface ScanLogSubscriber {

    void subscribe(Scan.ScanId scanId, ScanLogListener listener);

    void unsubscribe(Scan.ScanId scanId, ScanLogListener listener);
}
