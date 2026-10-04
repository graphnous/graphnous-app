package dev.graphnous.application.capability;

import java.util.List;

/**
 * Resolves the capabilities available in this deployment.
 */
public interface CapabilityResolver {

    List<Capability> resolve();

    boolean isAuthorizationEnabled();

}
