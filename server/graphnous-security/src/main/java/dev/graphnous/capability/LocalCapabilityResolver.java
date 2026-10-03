package dev.graphnous.capability;

import dev.graphnous.application.capability.Capability;
import dev.graphnous.application.capability.CapabilityResolver;

import java.util.List;

/**
 * A local deployment has every capability.
 */
public class LocalCapabilityResolver implements CapabilityResolver {

    @Override
    public List<Capability> resolve() {
        return List.of(Capability.values());
    }

}
