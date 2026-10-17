package dev.graphnous.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Which GitHub App the server is: the one its configuration names, or else
 * the one setup registered, read once and again after setup changes it.
 */
class AppIdentities {

    enum Source {
        /**
         * graphnous.github.app-id and the rest of the configuration.
         */
        CONFIGURATION,
        /**
         * Registered on GitHub by setup, and stored.
         */
        SETUP
    }

    /**
     * @param slug    its name in GitHub's urls; null when the configuration
     *                names it, as that does not say
     * @param htmlUrl its page on GitHub; null likewise
     */
    record Identity(String appId, String slug, String name, String htmlUrl, AppKey key, String webhookSecret, Source source) {

        @Override
        public String toString() {
            return "Identity[appId=" + appId + ", slug=" + slug + ", source=" + source + "]";
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AppIdentities.class);

    private final Identity configured;
    private final StoredApps stored;

    private volatile Optional<Identity> current;

    /**
     * @throws IllegalArgumentException when the configuration names an App
     *                                  with a key that is not one
     */
    AppIdentities(final GitHubProperties properties, final String privateKey, final StoredApps stored) {
        this.configured = properties.appId() == null || properties.appId().isBlank()
            ? null
            : new Identity(
                properties.appId().trim(), null, null, null,
                AppKey.of(properties.appId(), privateKey),
                properties.webhookSecret(),
                Source.CONFIGURATION
            );
        this.stored = stored;
    }

    Optional<Identity> current() {
        var identity = current;

        if (identity == null) {
            identity = load();
            current = identity;
        }

        return identity;
    }

    /**
     * The App, or why there is none.
     *
     * @throws IllegalStateException when the server is no App yet
     */
    Identity require() {
        return current().orElseThrow(() -> new IllegalStateException(
            "The GitHub App is not set up: register it in Graphnous's settings, or configure graphnous.github.app-id"
        ));
    }

    boolean isConfigured() {
        return configured != null;
    }

    /**
     * Reads the stored App again, as after setup.
     */
    void reload() {
        current = null;
    }

    private Optional<Identity> load() {
        if (configured != null) {
            return Optional.of(configured);
        }

        try {
            return stored.find().map(app -> new Identity(
                app.appId(), app.slug(), app.name(), app.htmlUrl(),
                AppKey.of(app.appId(), app.privateKey()),
                app.webhookSecret(),
                Source.SETUP
            ));
        } catch (final RuntimeException e) {
            log.error("Reading the GitHub App setup registered failed", e);
            return Optional.empty();
        }
    }
}
