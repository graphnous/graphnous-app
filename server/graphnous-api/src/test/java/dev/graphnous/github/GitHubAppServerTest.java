package dev.graphnous.github;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The server with the GitHub App on: its webhooks through the whole server,
 * and the pull requests' scans in PostgreSQL, with the Flyway migrations.
 * Skipped when Docker is not available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class GitHubAppServerTest {

    private static final String SECRET = "webhook secret";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void properties(final DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        final var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        final var key = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";

        registry.add("graphnous.github.enabled", () -> "true");
        registry.add("graphnous.github.app-id", () -> "12345");
        registry.add("graphnous.github.private-key", () -> key);
        registry.add("graphnous.github.webhook-secret", () -> SECRET);

        final var secretsKey = new byte[32];
        new java.security.SecureRandom().nextBytes(secretsKey);
        registry.add("graphnous.secrets.key", () -> Base64.getEncoder().encodeToString(secretsKey));
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PullRequestScans pullRequestScans;

    @Autowired
    private StoredApps storedApps;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void answersGitHubsDeliveriesSignedWithTheSecret() throws Exception {
        final var body = "{\"zen\": \"Keep it logically awesome.\", \"hook_id\": 1}";

        mvc.perform(post(GitHubWebhookController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", "ping")
                .header("X-Hub-Signature-256", signature(body))
                .content(body))
            .andExpect(status().isAccepted());

        mvc.perform(post(GitHubWebhookController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", "ping")
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void keepsThePullRequestsScansUntilTheyAreReported() {
        final var project = Project.ProjectId.generate();
        final var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        final var first = scan(project, 3, now.minusSeconds(60));
        final var second = scan(project, 3, now);
        final var otherProject = scan(Project.ProjectId.generate(), 3, now.plusSeconds(60));

        pullRequestScans.findAll().forEach(scan -> pullRequestScans.claim(scan.scanId()));

        pullRequestScans.save(first);
        pullRequestScans.save(second);
        pullRequestScans.save(otherProject);

        assertThat(pullRequestScans.find(first.scanId())).contains(first);
        // A newer push's scan of the same project replaces it, another project's does not
        assertThat(pullRequestScans.isSuperseded(first)).isTrue();
        assertThat(pullRequestScans.isSuperseded(second)).isFalse();

        // Once, to one caller
        assertThat(pullRequestScans.claim(first.scanId())).isTrue();
        assertThat(pullRequestScans.claim(first.scanId())).isFalse();

        assertThat(pullRequestScans.find(first.scanId())).isEmpty();
        assertThat(pullRequestScans.findAll()).containsExactly(second, otherProject);
        assertThat(pullRequestScans.find(second.scanId())).contains(second);
    }

    @Test
    void keepsTheAppSetupRegisteredWithItsSecretsEncrypted() {
        final var app = new StoredApp(
            "777", "graphnous-acme", "Graphnous Acme", "https://github.com/apps/graphnous-acme",
            "-----BEGIN RSA PRIVATE KEY-----\nMIIE\n-----END RSA PRIVATE KEY-----\n", "webhook secret",
            Instant.now().truncatedTo(ChronoUnit.MICROS)
        );

        storedApps.save(app);
        storedApps.save(app);

        assertThat(storedApps.find()).contains(app);
        assertThat(jdbc.sql("SELECT private_key || webhook_secret FROM github_apps").query(String.class).single())
            .doesNotContain("PRIVATE KEY", "webhook secret");

        storedApps.delete();

        assertThat(storedApps.find()).isEmpty();
    }

    @Test
    void refusesACallbackOfNoSetupWithoutAToken() throws Exception {
        mvc.perform(get(GitHubSetupController.CALLBACK).queryParam("code", "code-1").queryParam("state", "made-up"))
            .andExpect(status().isBadRequest());
    }

    private static PullRequestScan scan(final Project.ProjectId project, final int pullRequest, final Instant createdAt) {
        return new PullRequestScan(
            Scan.ScanId.generate(), project, 42, "acme/shop", pullRequest, "abc123", "main", 7, createdAt
        );
    }

    private static String signature(final String body) {
        return WebhookSignature.PREFIX
            + HexFormat.of().formatHex(WebhookSignature.sign(SECRET, body.getBytes(StandardCharsets.UTF_8)));
    }
}
