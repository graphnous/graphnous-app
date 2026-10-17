package dev.graphnous.github;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppIdentitiesTest {

    @Mock
    private StoredApps stored;

    private final StoredApp app = new StoredApp(
        "777", "graphnous-acme", "Graphnous Acme", "https://github.com/apps/graphnous-acme",
        AppKeyTest.pkcs1Pem(), "stored secret", Instant.parse("2026-10-17T12:00:00Z")
    );

    @Test
    void isTheAppTheConfigurationNames() {
        final var identities = new AppIdentities(properties("12345", "configured secret"), AppKeyTest.pkcs1Pem(), stored);

        assertThat(identities.isConfigured()).isTrue();
        assertThat(identities.require()).satisfies(identity -> {
            assertThat(identity.appId()).isEqualTo("12345");
            assertThat(identity.webhookSecret()).isEqualTo("configured secret");
            assertThat(identity.source()).isEqualTo(AppIdentities.Source.CONFIGURATION);
        });
        verify(stored, never()).find();
    }

    @Test
    void isTheAppSetupRegisteredOtherwiseReadOnceUntilReloaded() {
        when(stored.find()).thenReturn(Optional.empty(), Optional.of(app));

        final var identities = new AppIdentities(properties(null, null), null, stored);

        assertThat(identities.isConfigured()).isFalse();
        assertThat(identities.current()).isEmpty();
        assertThat(identities.current()).isEmpty();
        assertThatThrownBy(identities::require).hasMessageContaining("not set up");

        identities.reload();

        assertThat(identities.require()).satisfies(identity -> {
            assertThat(identity.appId()).isEqualTo("777");
            assertThat(identity.slug()).isEqualTo("graphnous-acme");
            assertThat(identity.webhookSecret()).isEqualTo("stored secret");
            assertThat(identity.source()).isEqualTo(AppIdentities.Source.SETUP);
            assertThat(identity.toString()).doesNotContain("stored secret");
        });
        verify(stored, times(2)).find();
    }

    @Test
    void isNoAppWhenTheStoredOneCannotBeRead() {
        when(stored.find()).thenThrow(new IllegalStateException("Decrypting github.private-key failed"));

        assertThat(new AppIdentities(properties(" ", null), null, stored).current()).isEmpty();
    }

    static GitHubProperties properties(final String appId, final String webhookSecret) {
        return new GitHubProperties(
            true, appId, null, null, webhookSecret, "https://api.github.com", UUID.randomUUID(), true, false, null, null
        );
    }
}
