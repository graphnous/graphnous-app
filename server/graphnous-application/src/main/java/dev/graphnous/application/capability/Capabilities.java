package dev.graphnous.application.capability;

import java.util.List;

/**
 * What this deployment offers: its capabilities, and whether it authorizes callers.
 */
public record Capabilities(
    List<Capability> capabilities,
    Authorization authorization
) {

    public record Authorization(
        boolean enabled
    ) {
    }
}
