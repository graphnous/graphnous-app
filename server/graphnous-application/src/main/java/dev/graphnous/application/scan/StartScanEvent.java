package dev.graphnous.application.scan;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.event.ApplicationEvent;
import dev.graphnous.domain.scan.Scan;

public class StartScanEvent implements ApplicationEvent {

    private final Scan.ScanId scanId;
    private final RequestContext context;

    public StartScanEvent(
        final Scan.ScanId scanId,
        final RequestContext context
    ) {
        this.scanId = scanId;
        this.context = context;
    }

    public Scan.ScanId getScanId() {
        return this.scanId;
    }

    public RequestContext getContext() {
        return this.context;
    }
}
