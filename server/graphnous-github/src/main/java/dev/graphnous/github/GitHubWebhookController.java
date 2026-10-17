package dev.graphnous.github;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * Where GitHub delivers the App's webhooks: only those signed with its
 * webhook secret are handled. Not behind the API's access tokens, as GitHub
 * has none; the signature is what makes a delivery GitHub's.
 */
@RestController
@ConditionalOnBooleanProperty("graphnous.github.enabled")
public class GitHubWebhookController {

    public static final String PATH = "/github/webhooks";

    private final GitHubApp app;
    private final AppIdentities identities;
    private final JsonMapper json;

    GitHubWebhookController(final GitHubApp app, final AppIdentities identities, final JsonMapper json) {
        this.app = app;
        this.identities = identities;
        this.json = json;
    }

    /**
     * @return 401 when the delivery is not signed with the App's secret;
     *         503 while the server is no App yet
     */
    @PostMapping(PATH)
    public ResponseEntity<Void> deliver(
        @RequestHeader("X-GitHub-Event") final String event,
        @RequestHeader(value = "X-Hub-Signature-256", required = false) final String signature,
        @RequestBody final byte[] body
    ) {
        final var identity = identities.current();

        if (identity.isEmpty()) {
            return ResponseEntity.status(503).build();
        }

        if (!WebhookSignature.isValid(identity.get().webhookSecret(), body, signature)) {
            return ResponseEntity.status(401).build();
        }

        app.handle(event, json.readTree(body));

        return ResponseEntity.accepted().build();
    }
}
