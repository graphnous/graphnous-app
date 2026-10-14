package dev.graphnous.application.scan;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.event.ApplicationEvent;
import dev.graphnous.domain.scan.Scan;

import java.util.Set;

public class StartScanEvent implements ApplicationEvent {

    private final Scan.ScanId scanId;
    private final RequestContext context;
    private final Set<String> enhancers;

    /**
     * @param enhancers the names of the enhancers to run on the scan's
     *                  results; null for every installed enhancer
     */
    public StartScanEvent(
        final Scan.ScanId scanId,
        final RequestContext context,
        final Set<String> enhancers
    ) {
        this.scanId = scanId;
        this.context = context;
        this.enhancers = enhancers;
    }

    public Scan.ScanId getScanId() {
        return this.scanId;
    }

    public RequestContext getContext() {
        return this.context;
    }

    /**
     * The names of the enhancers to run; null for every installed one.
     */
    public Set<String> getEnhancers() {
        return this.enhancers;
    }
}
