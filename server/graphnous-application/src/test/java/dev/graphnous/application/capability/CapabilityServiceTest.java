package dev.graphnous.application.capability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapabilityServiceTest {

    @Mock
    private CapabilityResolver capabilityResolver;

    @Test
    void returnsTheResolvedCapabilities() {
        when(capabilityResolver.resolve()).thenReturn(List.of(Capability.SCANNING, Capability.AI));

        final var service = new CapabilityService(capabilityResolver);

        assertThat(service.getCapabilities().capabilities()).containsExactly(Capability.SCANNING, Capability.AI);
    }

    @Test
    void returnsWhetherAuthorizationIsEnabled() {
        when(capabilityResolver.isAuthorizationEnabled()).thenReturn(true);

        final var service = new CapabilityService(capabilityResolver);

        assertThat(service.getCapabilities().authorization().enabled()).isTrue();
    }

}
