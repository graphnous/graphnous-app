package dev.graphnous.capability;

import dev.graphnous.application.capability.Capability;
import dev.graphnous.application.capability.CapabilityResolver;

import java.util.List;

/**
 * A local deployment has every capability, and does not authorize callers.
 */
public class LocalCapabilityResolver implements CapabilityResolver {

    @Override
    public List<Capability> resolve() {
        return List.of(Capability.values());
    }

    @Override
    public boolean isAuthorizationEnabled() {
        return false;
    }

}
