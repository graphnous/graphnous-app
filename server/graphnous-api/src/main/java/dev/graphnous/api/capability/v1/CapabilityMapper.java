package dev.graphnous.api.capability.v1;

import dev.graphnous.api.v1.generated.capability.CapabilitiesResponse;
import dev.graphnous.api.v1.generated.capability.Capability;

import java.util.LinkedHashSet;
import java.util.List;

class CapabilityMapper {

    public Capability fromDomain(
        final dev.graphnous.application.capability.Capability domain
    ) {
        return Capability.fromValue(domain.name());
    }

    public CapabilitiesResponse toResponse(
        final List<dev.graphnous.application.capability.Capability> domain
    ) {
        final var response = new CapabilitiesResponse();

        response.setCapabilities(
            new LinkedHashSet<>(domain.stream().map(this::fromDomain).toList())
        );

        return response;
    }
}
