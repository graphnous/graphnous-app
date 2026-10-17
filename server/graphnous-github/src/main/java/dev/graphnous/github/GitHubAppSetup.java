package dev.graphnous.github;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.exception.ConflictException;
import dev.graphnous.application.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers the GitHub App for this server, from GitHub's manifest flow:
 * <ol>
 *     <li>{@link #start} gives the manifest, which the browser posts to
 *     GitHub, with a state only this server knows;</li>
 *     <li>GitHub registers the App as the user confirms, and sends the
 *     browser back to the callback with a code;</li>
 *     <li>{@link #complete} exchanges the code for the App's id, key and
 *     webhook secret, stores them, and the server is the App from then on,
 *     without a restart.</li>
 * </ol>
 * An App the configuration names takes precedence, and setup leaves it be.
 */
public class GitHubAppSetup {

    /**
     * How long a state of {@link #start} is good for.
     */
    static final Duration STATE_LIFETIME = Duration.ofHours(1);

    static final Map<String, String> PERMISSIONS = Map.of(
        "checks", "write",
        "pull_requests", "write",
        "contents", "read",
        "metadata", "read"
    );

    static final List<String> EVENTS = List.of("push", "pull_request");

    private static final Logger log = LoggerFactory.getLogger(GitHubAppSetup.class);

    private final GitHubClient github;
    private final AppIdentities identities;
    private final StoredApps stored;
    private final AuthorizationService authorizationService;
    private final String webUrl;
    private final String serverUrl;
    private final String webhookUrl;
    private final Clock clock;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> states = new ConcurrentHashMap<>();

    /**
     * @param webUrl    GitHub's site, where Apps are registered, such as
     *                  https://github.com
     * @param serverUrl  where GitHub reaches this server, if configured
     * @param webhookUrl where GitHub delivers the webhooks, if not to the
     *                   server, as configured
     */
    GitHubAppSetup(
        final GitHubClient github,
        final AppIdentities identities,
        final StoredApps stored,
        final AuthorizationService authorizationService,
        final String webUrl,
        final String serverUrl,
        final String webhookUrl,
        final Clock clock
    ) {
        this.github = github;
        this.identities = identities;
        this.stored = stored;
        this.authorizationService = authorizationService;
        this.webUrl = webUrl;
        this.serverUrl = blankToNull(serverUrl);
        this.webhookUrl = blankToNull(webhookUrl);
        this.clock = clock;
    }

    /**
     * @param source    where the App comes from: CONFIGURATION or SETUP;
     *                  null when there is none
     * @param error     why GitHub would not answer as the App, if it did
     *                  not, such as a revoked key
     * @param blocked   why setup cannot register an App, if it cannot
     * @param serverUrl where GitHub would reach this server, as configured
     */
    public record Status(
        String source,
        String appId,
        String slug,
        String name,
        String htmlUrl,
        String installUrl,
        String error,
        String blocked,
        String serverUrl
    ) {
    }

    /**
     * @param action   where the browser posts the manifest, as the form
     *                 field manifest
     * @param manifest the App to register, as GitHub takes it
     */
    public record Manifest(String action, Map<String, Object> manifest) {
    }

    public Status status(final RequestContext context) {
        authorizationService.authorize(context, Permission.INTEGRATION_READ);

        final var blocked = identities.isConfigured()
            ? "The configuration names the App (graphnous.github.app-id)"
            : stored.canStore() ? null : "Set a secrets key (graphnous.secrets.key) to keep the App's secrets";

        return identities.current()
            .map(identity -> {
                try {
                    final var app = github.app();

                    return new Status(identity.source().name(), identity.appId(), app.slug(), app.name(), app.html_url(),
                        app.html_url() + "/installations/new", null, blocked, serverUrl);
                } catch (final RuntimeException e) {
                    return new Status(identity.source().name(), identity.appId(), identity.slug(), identity.name(), identity.htmlUrl(),
                        identity.htmlUrl() == null ? null : identity.htmlUrl() + "/installations/new",
                        "GitHub did not answer as the App: " + e.getMessage(), blocked, serverUrl);
                }
            })
            .orElseGet(() -> new Status(null, null, null, null, null, null, null, blocked, serverUrl));
    }

    /**
     * @param name         the App's name on GitHub, which must be unused
     * @param organization the GitHub organization to register it in; the
     *                     user's own account when null
     * @param serverUrl    where GitHub reaches this server, when the
     *                     configuration does not say
     * @param webhookUrl   where GitHub delivers the webhooks, when not to
     *                     the server and the configuration does not say
     * @throws ConflictException   when the configuration names an App, or
     *                             there is no secrets key to keep one
     * @throws ValidationException when it is not known where GitHub
     *                             reaches the server, or a value is not
     *                             well formed
     */
    public Manifest start(
        final RequestContext context,
        final String name,
        final String organization,
        final String serverUrl,
        final String webhookUrl
    ) {
        authorizationService.authorize(context, Permission.INTEGRATION_MANAGE);

        if (identities.isConfigured()) {
            throw new ConflictException("The configuration names the GitHub App (graphnous.github.app-id); setup leaves it be");
        }

        if (!stored.canStore()) {
            throw new ConflictException("Set a secrets key (graphnous.secrets.key, GRAPHNOUS_SECRETS_KEY) to keep the GitHub App's secrets");
        }

        final var server = url(this.serverUrl != null ? this.serverUrl : blankToNull(serverUrl));
        final var hooks = this.webhookUrl != null ? url(this.webhookUrl)
            : blankToNull(webhookUrl) != null ? url(webhookUrl)
            : server + GitHubWebhookController.PATH;
        final var appName = blankToNull(name) == null ? "Graphnous" : name.trim();
        final var owner = blankToNull(organization);

        if (owner != null && !owner.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})")) {
            throw new ValidationException("Not a GitHub organization: " + owner);
        }

        final var now = clock.instant();
        states.values().removeIf(expires -> now.isAfter(expires));

        final var state = state();
        states.put(state, now.plus(STATE_LIFETIME));

        final var manifest = new LinkedHashMap<String, Object>();
        manifest.put("name", appName);
        manifest.put("url", server);
        manifest.put("hook_attributes", Map.of("url", hooks, "active", true));
        manifest.put("redirect_url", server + GitHubSetupController.CALLBACK);
        manifest.put("public", false);
        manifest.put("default_permissions", PERMISSIONS);
        manifest.put("default_events", EVENTS);

        final var path = owner == null
            ? "/settings/apps/new"
            : "/organizations/" + owner + "/settings/apps/new";

        return new Manifest(webUrl + path + "?state=" + URLEncoder.encode(state, StandardCharsets.UTF_8), manifest);
    }

    /**
     * Stores the App GitHub registered, once for each state of
     * {@link #start}.
     *
     * @return where to install the App
     * @throws ValidationException when the state is not one of a setup
     *                             that is under way
     */
    public String complete(final String code, final String state) {
        final var expires = state == null ? null : states.remove(state);

        if (expires == null || clock.instant().isAfter(expires)) {
            throw new ValidationException("This setup of the GitHub App is not under way here, or took too long: start it again");
        }

        if (code == null || code.isBlank() || !code.matches("[A-Za-z0-9_-]+")) {
            throw new ValidationException("GitHub sent no code for the App");
        }

        final var app = github.convert(code);

        stored.save(new StoredApp(
            String.valueOf(app.id()), app.slug(), app.name(), app.html_url(), app.pem(), app.webhook_secret(), clock.instant()
        ));
        identities.reload();
        github.reset();

        log.info("Registered the GitHub App {} ({})", app.slug(), app.id());

        return app.html_url() + "/installations/new";
    }

    /**
     * Forgets the App setup registered; it stays registered on GitHub,
     * where it is deleted.
     *
     * @throws ConflictException when the configuration names the App
     */
    public void disconnect(final RequestContext context) {
        authorizationService.authorize(context, Permission.INTEGRATION_MANAGE);

        if (identities.isConfigured()) {
            throw new ConflictException("The configuration names the GitHub App (graphnous.github.app-id)");
        }

        stored.delete();
        identities.reload();
        github.reset();
    }

    private String state() {
        final var bytes = new byte[24];
        random.nextBytes(bytes);

        return HexFormat.of().formatHex(bytes);
    }

    private static String url(final String url) {
        if (url == null) {
            throw new ValidationException(
                "Where GitHub reaches this server is needed for its webhooks: configure graphnous.github.server-url, or give it"
            );
        }

        final URI uri;

        try {
            uri = URI.create(url.trim());
        } catch (final IllegalArgumentException e) {
            throw new ValidationException("Not a url: " + url);
        }

        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null) {
            throw new ValidationException("Not an http(s) url: " + url);
        }

        return url.trim().replaceAll("/+$", "");
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
