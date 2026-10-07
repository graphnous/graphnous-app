package dev.graphnous.application.scan;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.event.ApplicationEvent;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.domain.scan.Scan;

import java.util.List;

/**
 * A scan was created from uploaded results, which are to be stored and
 * enhanced. The results travel with the event; they are not kept elsewhere.
 */
public class UploadedScanEvent implements ApplicationEvent {

    private final Scan.ScanId scanId;
    private final List<ScanResult> results;
    private final RequestContext context;

    public UploadedScanEvent(
        final Scan.ScanId scanId,
        final List<ScanResult> results,
        final RequestContext context
    ) {
        this.scanId = scanId;
        this.results = List.copyOf(results);
        this.context = context;
    }

    public Scan.ScanId getScanId() {
        return this.scanId;
    }

    public List<ScanResult> getResults() {
        return this.results;
    }

    public RequestContext getContext() {
        return this.context;
    }
}
