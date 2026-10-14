package dev.graphnous.application.enhancer;

import dev.graphnous.application.event.ApplicationEvent;
import dev.graphnous.domain.scan.Scan;

/**
 * A completed scan was enhanced again: what the enhancers added to it is
 * replaced.
 */
public record ScanEnhancedEvent(Scan.ScanId scanId) implements ApplicationEvent {
}
