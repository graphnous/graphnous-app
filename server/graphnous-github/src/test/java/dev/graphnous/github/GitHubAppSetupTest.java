package dev.graphnous.github;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.ConflictException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.organization.OrganizationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubAppSetupTest {

    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

    @Mock
    private GitHubClient github;

    @Mock
    private AppIdentities identities;

    @Mock
    private StoredApps stored;

    @Mock
    private AuthorizationService authorizationService;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()), new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final MovingClock clock = new MovingClock();

    @Test
    void givesTheManifestOfTheAppToRegister() {
        when(stored.canStore()).thenReturn(true);

        final var manifest = setup(null).start(context, "Graphnous Acme", null, "https://graphnous.acme.test/", null);

        assertThat(manifest.action()).startsWith("https://github.com/settings/apps/new?state=");
        assertThat(manifest.manifest())
            .containsEntry("name", "Graphnous Acme")
            .containsEntry("url", "https://graphnous.acme.test")
            .containsEntry("hook_attributes", Map.of("url", "https://graphnous.acme.test/github/webhooks", "active", true))
            .containsEntry("redirect_url", "https://graphnous.acme.test/github/setup/callback")
            .containsEntry("public", false)
            .containsEntry("default_permissions", GitHubAppSetup.PERMISSIONS)
            .containsEntry("default_events", GitHubAppSetup.EVENTS);
        verify(authorizationService).authorize(context, Permission.INTEGRATION_MANAGE);
    }

    @Test
    void registersInAnOrganizationAtTheConfiguredServerUrl() {
        when(stored.canStore()).thenReturn(true);

        final var manifest = setup("https://graphnous.example.test").start(context, null, "acme-corp", "https://ignored.test", null);

        assertThat(manifest.action()).startsWith("https://github.com/organizations/acme-corp/settings/apps/new?state=");
        assertThat(manifest.manifest())
            .containsEntry("name", "Graphnous")
            .containsEntry("url", "https://graphnous.example.test");
    }

    @Test
    void sendsTheWebhooksWhereTheyAreForwardedFrom() {
        when(stored.canStore()).thenReturn(true);

        final var manifest = setup("http://localhost:1337").start(context, null, null, null, "https://smee.io/aBcD1234");

        assertThat(manifest.manifest())
            .containsEntry("hook_attributes", Map.of("url", "https://smee.io/aBcD1234", "active", true))
            .containsEntry("redirect_url", "http://localhost:1337/github/setup/callback");
    }

    @Test
    void refusesWhatItCannotRegister() {
        when(identities.isConfigured()).thenReturn(true);

        assertThatThrownBy(() -> setup(null).start(context, null, null, "https://graphnous.acme.test", null))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("graphnous.github.app-id");

        when(identities.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> setup(null).start(context, null, null, "https://graphnous.acme.test", null))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("graphnous.secrets.key");

        when(stored.canStore()).thenReturn(true);

        assertThatThrownBy(() -> setup(null).start(context, null, null, null, null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("graphnous.github.server-url");
        assertThatThrownBy(() -> setup(null).start(context, null, null, "ftp://graphnous.acme.test", null))
            .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> setup(null).start(context, null, "acme/../evil", "https://graphnous.acme.test", null))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void storesTheAppGitHubRegisteredOnceForAState() {
        when(stored.canStore()).thenReturn(true);
        when(github.convert("code-1")).thenReturn(new GitHubClient.Conversion(
            777, "graphnous-acme", "Graphnous Acme", "https://github.com/apps/graphnous-acme", "pem", "secret"
        ));

        final var setup = setup(null);
        final var state = state(setup.start(context, null, null, "https://graphnous.acme.test", null));

        assertThat(setup.complete("code-1", state)).isEqualTo("https://github.com/apps/graphnous-acme/installations/new");

        final var app = ArgumentCaptor.forClass(StoredApp.class);
        verify(stored).save(app.capture());
        assertThat(app.getValue()).isEqualTo(new StoredApp(
            "777", "graphnous-acme", "Graphnous Acme", "https://github.com/apps/graphnous-acme", "pem", "secret", NOW
        ));
        verify(identities).reload();
        verify(github).reset();

        assertThatThrownBy(() -> setup.complete("code-1", state)).isInstanceOf(ValidationException.class);
    }

    @Test
    void refusesACallbackOfNoSetupOrOneThatTookTooLong() {
        when(stored.canStore()).thenReturn(true);

        final var setup = setup(null);
        final var state = state(setup.start(context, null, null, "https://graphnous.acme.test", null));

        assertThatThrownBy(() -> setup.complete("code-1", "made-up")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> setup.complete("code-1", null)).isInstanceOf(ValidationException.class);

        clock.now = NOW.plus(GitHubAppSetup.STATE_LIFETIME).plusSeconds(1);

        assertThatThrownBy(() -> setup.complete("code-1", state))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("start it again");
        verify(github, never()).convert(any());
    }

    @Test
    void saysWhatTheAppIsAsGitHubHasIt() {
        when(identities.current()).thenReturn(Optional.of(identity()));
        when(stored.canStore()).thenReturn(true);
        when(github.app()).thenReturn(new GitHubClient.App(777, "graphnous-acme", "Graphnous Acme", "https://github.com/apps/graphnous-acme"));

        assertThat(setup("https://graphnous.acme.test").status(context)).isEqualTo(new GitHubAppSetup.Status(
            "SETUP", "777", "graphnous-acme", "Graphnous Acme", "https://github.com/apps/graphnous-acme",
            "https://github.com/apps/graphnous-acme/installations/new", null, null, "https://graphnous.acme.test"
        ));
        verify(authorizationService).authorize(context, Permission.INTEGRATION_READ);
    }

    @Test
    void saysWhenGitHubDoesNotAnswerAsTheAppOrThereIsNone() {
        when(identities.current()).thenReturn(Optional.of(identity()), Optional.empty());
        when(github.app()).thenThrow(new IllegalStateException("401 Unauthorized"));

        final var setup = setup(null);

        assertThat(setup.status(context)).satisfies(status -> {
            assertThat(status.source()).isEqualTo("SETUP");
            assertThat(status.error()).isEqualTo("GitHub did not answer as the App: 401 Unauthorized");
            assertThat(status.installUrl()).isEqualTo("https://github.com/apps/graphnous-acme/installations/new");
        });
        assertThat(setup.status(context)).satisfies(status -> {
            assertThat(status.source()).isNull();
            assertThat(status.blocked()).contains("graphnous.secrets.key");
        });
    }

    @Test
    void forgetsTheAppSetupRegistered() {
        setup(null).disconnect(context);

        verify(stored).delete();
        verify(identities).reload();
        verify(github).reset();
    }

    @Test
    void leavesTheAppTheConfigurationNames() {
        when(identities.isConfigured()).thenReturn(true);

        assertThatThrownBy(() -> setup(null).disconnect(context)).isInstanceOf(ConflictException.class);
        verify(stored, never()).delete();
    }

    private AppIdentities.Identity identity() {
        return new AppIdentities.Identity(
            "777", "graphnous-acme", "Graphnous Acme", "https://github.com/apps/graphnous-acme",
            null, "secret", AppIdentities.Source.SETUP
        );
    }

    private static String state(final GitHubAppSetup.Manifest manifest) {
        return URI.create(manifest.action()).getQuery().substring("state=".length());
    }

    private GitHubAppSetup setup(final String serverUrl) {
        return new GitHubAppSetup(github, identities, stored, authorizationService, "https://github.com", serverUrl, null, clock);
    }

    private static final class MovingClock extends Clock {

        private Instant now = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
