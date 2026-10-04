package dev.graphnous.application.capability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CapabilityService {

    private final CapabilityResolver capabilityResolver;

    private static final Logger log = LoggerFactory.getLogger(CapabilityService.class);

    public CapabilityService(
        final CapabilityResolver capabilityResolver
    ) {
        this.capabilityResolver = capabilityResolver;
    }

    public Capabilities getCapabilities() {
        log.debug("Getting capabilities");

        return new Capabilities(
            capabilityResolver.resolve(),
            new Capabilities.Authorization(capabilityResolver.isAuthorizationEnabled())
        );
    }
}
