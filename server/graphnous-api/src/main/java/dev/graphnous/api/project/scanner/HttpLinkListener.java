package dev.graphnous.api.project.scanner;

import dev.graphnous.application.enhancer.ScanEnhancedEvent;
import dev.graphnous.application.scan.ScanCompletedEvent;
import dev.graphnous.application.scan.links.HttpLinkService;
import dev.graphnous.domain.scan.Scan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Stores the HTTP links of every scan that completes or is enhanced again,
 * in the background. Failing to does not fail the scan: its links are
 * found again when they are next read.
 */
@Component
public class HttpLinkListener {

    private static final Logger log = LoggerFactory.getLogger(HttpLinkListener.class);

    private final HttpLinkService httpLinkService;

    public HttpLinkListener(final HttpLinkService httpLinkService) {
        this.httpLinkService = httpLinkService;
    }

    @EventListener(ScanCompletedEvent.class)
    @Async
    public void completed(final ScanCompletedEvent event) {
        refresh(event.getScanId());
    }

    @EventListener(ScanEnhancedEvent.class)
    @Async
    public void enhanced(final ScanEnhancedEvent event) {
        refresh(event.scanId());
    }

    private void refresh(final Scan.ScanId scanId) {
        try {
            httpLinkService.refresh(scanId);
        } catch (final RuntimeException e) {
            log.warn("Storing the HTTP links of a scan failed scanId={}", scanId.id(), e);
        }
    }
}
