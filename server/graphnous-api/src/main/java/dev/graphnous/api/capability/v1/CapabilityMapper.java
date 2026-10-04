package dev.graphnous.api.capability.v1;

import dev.graphnous.api.v1.generated.capability.CapabilitiesResponse;
import dev.graphnous.api.v1.generated.capability.CapabilitiesResponseAuthorization;
import dev.graphnous.api.v1.generated.capability.Capability;
import dev.graphnous.application.capability.Capabilities;

import java.util.LinkedHashSet;

class CapabilityMapper {

    public Capability fromDomain(
        final dev.graphnous.application.capability.Capability domain
    ) {
        return Capability.fromValue(domain.name());
    }

    public CapabilitiesResponse toResponse(
        final Capabilities domain
    ) {
        final var response = new CapabilitiesResponse();

        response.setCapabilities(
            new LinkedHashSet<>(domain.capabilities().stream().map(this::fromDomain).toList())
        );
        response.setAuthorization(
            new CapabilitiesResponseAuthorization(domain.authorization().enabled())
        );

        return response;
    }
}
