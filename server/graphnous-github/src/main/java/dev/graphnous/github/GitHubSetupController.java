package dev.graphnous.github;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.exception.ValidationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Setting the GitHub App up: in the API, as the user, what the App is, and
 * the manifest to register it with; and the callback GitHub sends the
 * browser back to, which has no token, so only the state it carries, which
 * the API gave out, lets it in.
 * <p>
 * Not in the published OpenAPI spec yet, so its models are its own.
 */
@RestController
@ConditionalOnBooleanProperty("graphnous.github.enabled")
public class GitHubSetupController {

    public static final String CALLBACK = "/github/setup/callback";

    private final GitHubAppSetup setup;
    private final RequestContextProvider contextProvider;

    public GitHubSetupController(final GitHubAppSetup setup, final RequestContextProvider contextProvider) {
        this.setup = setup;
        this.contextProvider = contextProvider;
    }

    /**
     * @param name         the App's name on GitHub; Graphnous when left out
     * @param organization the GitHub organization to register it in; the
     *                     user's account when left out
     * @param serverUrl    where GitHub reaches this server, when its
     *                     configuration does not say
     * @param webhookUrl   where GitHub delivers the webhooks, when not to
     *                     the server, such as a smee.io channel
     */
    public record ManifestRequest(String name, String organization, String serverUrl, String webhookUrl) {
    }

    @GetMapping("/api/v1/github/app")
    public GitHubAppSetup.Status app() {
        return setup.status(contextProvider.get());
    }

    /**
     * The manifest for the browser to post to GitHub, which registers the
     * App as the user confirms.
     */
    @PostMapping("/api/v1/github/app/manifest")
    public GitHubAppSetup.Manifest manifest(@RequestBody(required = false) final ManifestRequest request) {
        final var given = request == null ? new ManifestRequest(null, null, null, null) : request;

        return setup.start(contextProvider.get(), given.name(), given.organization(), given.serverUrl(), given.webhookUrl());
    }

    @DeleteMapping("/api/v1/github/app")
    public ResponseEntity<Void> disconnect() {
        setup.disconnect(contextProvider.get());

        return ResponseEntity.noContent().build();
    }

    /**
     * Stores the App GitHub registered, and sends the browser on to install
     * it; 400, in words, when the state is not of a setup under way.
     */
    @GetMapping(CALLBACK)
    public ResponseEntity<String> callback(
        @RequestParam(value = "code", required = false) final String code,
        @RequestParam(value = "state", required = false) final String state
    ) {
        try {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, setup.complete(code, state))
                .build();
        } catch (final ValidationException e) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body(e.getMessage());
        }
    }
}
