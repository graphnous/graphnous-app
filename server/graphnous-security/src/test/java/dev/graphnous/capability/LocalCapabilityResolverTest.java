package dev.graphnous.capability;

import dev.graphnous.application.capability.Capability;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCapabilityResolverTest {

    @Test
    void resolvesEveryCapability() {
        assertThat(new LocalCapabilityResolver().resolve())
            .containsExactlyInAnyOrder(Capability.values());
    }

    @Test
    void doesNotEnableAuthorization() {
        assertThat(new LocalCapabilityResolver().isAuthorizationEnabled()).isFalse();
    }

}
