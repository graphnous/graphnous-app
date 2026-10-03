package dev.graphnous.application.capability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CapabilityService {

    private final CapabilityResolver capabilityResolver;

    private static final Logger log = LoggerFactory.getLogger(CapabilityService.class);

    public CapabilityService(
        final CapabilityResolver capabilityResolver
    ) {
        this.capabilityResolver = capabilityResolver;
    }

    public List<Capability> getCapabilities() {
        log.debug("Getting capabilities");

        return capabilityResolver.resolve();
    }
}
