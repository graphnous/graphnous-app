package dev.graphnous.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubClientTest {

    private static final String API = "https://api.github.test";
    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

    private MockRestServiceServer server;
    private RestClient.Builder rest;

    @BeforeEach
    void setUp() {
        rest = RestClient.builder();
        server = MockRestServiceServer.bindTo(rest).build();
    }

    @Test
    void actsAsTheInstallationWithATokenItReuses() {
        expectToken("tok-1", NOW.plusSeconds(3600));
        server.expect(requestTo(API + "/repos/acme/shop/check-runs"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer tok-1"))
            .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
            .andExpect(jsonPath("$.name").value("Graphnous impact: shop"))
            .andExpect(jsonPath("$.head_sha").value("abc123"))
            .andExpect(jsonPath("$.status").value("in_progress"))
            .andRespond(withSuccess("{\"id\": 7}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/acme/shop/check-runs/7"))
            .andExpect(method(HttpMethod.PATCH))
            .andExpect(header("Authorization", "Bearer tok-1"))
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.conclusion").value("success"))
            .andExpect(jsonPath("$.output.title").value("Nothing else uses what changed"))
            .andRespond(withSuccess());

        final var client = client(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(client.startCheckRun(42, "acme/shop", "Graphnous impact: shop", "abc123")).isEqualTo(7);
        client.completeCheckRun(42, "acme/shop", 7, "success", new GitHubClient.Output("Nothing else uses what changed", "…"));

        server.verify();
    }

    @Test
    void getsANewTokenShortlyBeforeTheLastExpires() {
        final var clock = new MovingClock(NOW);

        expectToken("tok-1", NOW.plusSeconds(3600));
        expectComment("Bearer tok-1");
        expectToken("tok-2", NOW.plusSeconds(7200));
        expectComment("Bearer tok-2");

        final var client = client(clock);

        client.comment(42, "acme/shop", 3, "Report");
        clock.now = NOW.plusSeconds(3600).minus(GitHubClient.TOKEN_MARGIN);
        client.comment(42, "acme/shop", 3, "Report");

        server.verify();
    }

    @Test
    void findsTheCommentWithTheMarkerOnAnyPage() {
        expectToken("tok-1", NOW.plusSeconds(3600));
        server.expect(requestTo(API + "/repos/acme/shop/issues/3/comments?per_page=100&page=1"))
            .andRespond(withSuccess(comments(1, GitHubClient.PAGE_SIZE), MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/acme/shop/issues/3/comments?per_page=100&page=2"))
            .andRespond(withSuccess(
                "[{\"id\": 500, \"body\": \"LGTM\"}, {\"id\": 501, \"body\": \"<!-- graphnous-impact -->\\n<!-- mine -->\\nReport\"}]",
                MediaType.APPLICATION_JSON
            ));

        assertThat(client(Clock.fixed(NOW, ZoneOffset.UTC)).findComment(42, "acme/shop", 3, "<!-- mine -->")).contains(501L);

        server.verify();
    }

    @Test
    void findsNoCommentAfterTheLastPage() {
        expectToken("tok-1", NOW.plusSeconds(3600));
        server.expect(requestTo(API + "/repos/acme/shop/issues/3/comments?per_page=100&page=1"))
            .andRespond(withSuccess(comments(1, 3), MediaType.APPLICATION_JSON));

        assertThat(client(Clock.fixed(NOW, ZoneOffset.UTC)).findComment(42, "acme/shop", 3, "<!-- mine -->")).isEmpty();
    }

    @Test
    void findsTheCommitABranchStartedFrom() {
        expectToken("tok-1", NOW.plusSeconds(3600));
        server.expect(requestTo(API + "/repos/acme/shop/compare/def456...abc123"))
            .andExpect(header("Authorization", "Bearer tok-1"))
            .andRespond(withSuccess(
                "{\"status\": \"diverged\", \"merge_base_commit\": {\"sha\": \"bbb111\"}, \"commits\": []}",
                MediaType.APPLICATION_JSON
            ));

        assertThat(client(Clock.fixed(NOW, ZoneOffset.UTC)).mergeBase(42, "acme/shop", "def456", "abc123")).contains("bbb111");

        server.verify();
    }

    @Test
    void editsAComment() {
        expectToken("tok-1", NOW.plusSeconds(3600));
        server.expect(requestTo(API + "/repos/acme/shop/issues/comments/501"))
            .andExpect(method(HttpMethod.PATCH))
            .andExpect(jsonPath("$.body").value("New report"))
            .andRespond(withSuccess());

        client(Clock.fixed(NOW, ZoneOffset.UTC)).editComment(42, "acme/shop", 501, "New report");

        server.verify();
    }

    @Test
    void findsTheInstallationOfARepositoryAsTheApp() {
        server.expect(requestTo(API + "/repos/acme/shop/installation"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(request -> assertThat(request.getHeaders().getFirst("Authorization")).startsWith("Bearer ey"))
            .andRespond(withSuccess("{\"id\": 42, \"account\": {\"login\": \"acme\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/acme/blog/installation"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        final var client = client(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(client.installationOf("acme/shop")).contains(42L);
        assertThat(client.installationOf("acme/blog")).isEmpty();
    }

    @Test
    void getsTheAppItIsAndTheAppAManifestRegistered() {
        server.expect(requestTo(API + "/app"))
            .andExpect(request -> assertThat(request.getHeaders().getFirst("Authorization")).startsWith("Bearer ey"))
            .andRespond(withSuccess(
                "{\"id\": 777, \"slug\": \"graphnous-acme\", \"name\": \"Graphnous Acme\", \"html_url\": \"https://github.com/apps/graphnous-acme\"}",
                MediaType.APPLICATION_JSON
            ));
        server.expect(requestTo(API + "/app-manifests/code-1/conversions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(request -> assertThat(request.getHeaders().containsHeader("Authorization")).isFalse())
            .andRespond(withSuccess(
                "{\"id\": 777, \"slug\": \"graphnous-acme\", \"name\": \"Graphnous Acme\", \"html_url\": \"https://github.com/apps/graphnous-acme\", "
                    + "\"pem\": \"-----BEGIN RSA PRIVATE KEY-----\", \"webhook_secret\": \"secret\", \"client_secret\": \"unused\"}",
                MediaType.APPLICATION_JSON
            ));

        final var client = client(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(client.app()).isEqualTo(new GitHubClient.App(777, "graphnous-acme", "Graphnous Acme", "https://github.com/apps/graphnous-acme"));
        assertThat(client.convert("code-1")).satisfies(app -> {
            assertThat(app.id()).isEqualTo(777);
            assertThat(app.pem()).isEqualTo("-----BEGIN RSA PRIVATE KEY-----");
            assertThat(app.webhook_secret()).isEqualTo("secret");
            assertThat(app.toString()).doesNotContain("secret");
        });

        server.verify();
    }

    @Test
    void forgetsTheTokensOfTheAppItWas() {
        expectToken("tok-1", NOW.plusSeconds(3600));
        expectComment("Bearer tok-1");
        expectToken("tok-2", NOW.plusSeconds(3600));
        expectComment("Bearer tok-2");

        final var client = client(Clock.fixed(NOW, ZoneOffset.UTC));

        client.comment(42, "acme/shop", 3, "Report");
        client.reset();
        client.comment(42, "acme/shop", 3, "Report");

        server.verify();
    }

    private void expectToken(final String token, final Instant expiresAt) {
        server.expect(once(), requestTo(API + "/app/installations/42/access_tokens"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(request -> assertThat(request.getHeaders().getFirst("Authorization")).startsWith("Bearer ey"))
            .andRespond(withSuccess(
                "{\"token\": \"" + token + "\", \"expires_at\": \"" + expiresAt + "\"}",
                MediaType.APPLICATION_JSON
            ));
    }

    private void expectComment(final String authorization) {
        server.expect(requestTo(API + "/repos/acme/shop/issues/3/comments"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", authorization))
            .andExpect(jsonPath("$.body").value("Report"))
            .andRespond(withSuccess());
    }

    private static String comments(final int from, final int count) {
        return IntStream.range(from, from + count)
            .mapToObj(id -> "{\"id\": " + id + ", \"body\": \"Comment " + id + "\"}")
            .collect(Collectors.joining(",", "[", "]"));
    }

    private GitHubClient client(final Clock clock) {
        final var key = AppKey.of("12345", AppKeyTest.pkcs1Pem());

        return new GitHubClient(rest, API, () -> key, clock);
    }

    private static final class MovingClock extends Clock {

        private Instant now;

        private MovingClock(final Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
