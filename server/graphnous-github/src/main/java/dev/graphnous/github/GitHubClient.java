package dev.graphnous.github;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * What the App does in GitHub, as an installation: check runs and comments
 * of pull requests. Each installation's token is reused until shortly
 * before it expires.
 */
public class GitHubClient {

    /**
     * How long before a token expires it is no longer used.
     */
    static final Duration TOKEN_MARGIN = Duration.ofMinutes(5);

    static final int PAGE_SIZE = 100;

    private final RestClient rest;
    private final Supplier<AppKey> key;
    private final Clock clock;

    private final Map<Long, InstallationToken> tokens = new ConcurrentHashMap<>();

    /**
     * @param key the App's key, as it is when asked: it may change, as
     *            when setup registers the App
     */
    GitHubClient(final RestClient.Builder rest, final String apiUrl, final Supplier<AppKey> key, final Clock clock) {
        this.rest = rest
            .baseUrl(apiUrl)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
        this.key = key;
        this.clock = clock;
    }

    record InstallationToken(String token, Instant expiresAt) {
    }

    record CheckRun(long id) {
    }

    record Comment(long id, String body) {
    }

    record Installation(long id) {
    }

    // As GitHub names its fields
    private record Comparison(Commit merge_base_commit) {
    }

    private record Commit(String sha) {
    }

    /**
     * The App as GitHub has it.
     */
    record App(long id, String slug, String name, String html_url) {
    }

    /**
     * A manifest's App, as GitHub registered it, with its secrets.
     */
    record Conversion(long id, String slug, String name, String html_url, String pem, String webhook_secret) {

        @Override
        public String toString() {
            return "Conversion[id=" + id + ", slug=" + slug + "]";
        }
    }

    /**
     * @param summary in Markdown, at most 65,535 characters
     */
    record Output(String title, String summary) {
    }

    /**
     * Starts a check run of the commit, in progress.
     *
     * @param repository its full name, such as acme/shop
     */
    long startCheckRun(final long installation, final String repository, final String name, final String headSha) {
        return rest.post()
            .uri("/repos/{owner}/{repo}/check-runs", repository(repository))
            .header(HttpHeaders.AUTHORIZATION, bearer(installation))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("name", name, "head_sha", headSha, "status", "in_progress"))
            .retrieve()
            .body(CheckRun.class)
            .id();
    }

    /**
     * @param conclusion success, neutral or failure
     */
    void completeCheckRun(
        final long installation,
        final String repository,
        final long checkRun,
        final String conclusion,
        final Output output
    ) {
        rest.patch()
            .uri("/repos/{owner}/{repo}/check-runs/{id}", repository(repository, "id", checkRun))
            .header(HttpHeaders.AUTHORIZATION, bearer(installation))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "status", "completed",
                "conclusion", conclusion,
                "completed_at", clock.instant().toString(),
                "output", Map.of("title", output.title(), "summary", output.summary())
            ))
            .retrieve()
            .toBodilessEntity();
    }

    /**
     * The first comment of the pull request that holds the marker.
     */
    Optional<Long> findComment(final long installation, final String repository, final int pullRequest, final String marker) {
        for (var page = 1; ; page++) {
            final var comments = rest.get()
                .uri(
                    "/repos/{owner}/{repo}/issues/{number}/comments?per_page={size}&page={page}",
                    repository(repository, "number", pullRequest, "size", PAGE_SIZE, "page", page)
                )
                .header(HttpHeaders.AUTHORIZATION, bearer(installation))
                .retrieve()
                .body(Comment[].class);

            if (comments == null) {
                return Optional.empty();
            }

            final var found = List.of(comments).stream()
                .filter(comment -> comment.body() != null && comment.body().contains(marker))
                .map(Comment::id)
                .findFirst();

            if (found.isPresent() || comments.length < PAGE_SIZE) {
                return found;
            }
        }
    }

    void comment(final long installation, final String repository, final int pullRequest, final String body) {
        rest.post()
            .uri("/repos/{owner}/{repo}/issues/{number}/comments", repository(repository, "number", pullRequest))
            .header(HttpHeaders.AUTHORIZATION, bearer(installation))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("body", body))
            .retrieve()
            .toBodilessEntity();
    }

    void editComment(final long installation, final String repository, final long comment, final String body) {
        rest.patch()
            .uri("/repos/{owner}/{repo}/issues/comments/{id}", repository(repository, "id", comment))
            .header(HttpHeaders.AUTHORIZATION, bearer(installation))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("body", body))
            .retrieve()
            .toBodilessEntity();
    }

    /**
     * The variables of a repository's path, its owner and name apart, as
     * the slash between them is not to be encoded; with more after them.
     */
    private static Map<String, Object> repository(final String repository, final Object... more) {
        final var slash = repository.indexOf('/');

        if (slash < 1 || slash == repository.length() - 1) {
            throw new IllegalArgumentException("Not a repository's full name: " + repository);
        }

        final var variables = new HashMap<String, Object>();
        variables.put("owner", repository.substring(0, slash));
        variables.put("repo", repository.substring(slash + 1));

        for (var i = 0; i < more.length; i += 2) {
            variables.put((String) more[i], more[i + 1]);
        }

        return variables;
    }

    /**
     * The commit a branch started from: the merge base of the base and the
     * head commits, as GitHub compares them.
     */
    Optional<String> mergeBase(final long installation, final String repository, final String base, final String head) {
        final var comparison = rest.get()
            .uri("/repos/{owner}/{repo}/compare/{basehead}", repository(repository, "basehead", base + "..." + head))
            .header(HttpHeaders.AUTHORIZATION, bearer(installation))
            .retrieve()
            .body(Comparison.class);

        return Optional.ofNullable(comparison)
            .map(Comparison::merge_base_commit)
            .map(Commit::sha);
    }

    /**
     * The App the key is of, which says the key works.
     */
    App app() {
        return rest.get()
            .uri("/app")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + key.get().jwt(clock.instant()))
            .retrieve()
            .body(App.class);
    }

    /**
     * The App GitHub registered from a manifest, by the code it redirected
     * back with, once: what setup stores.
     */
    Conversion convert(final String code) {
        return rest.post()
            .uri("/app-manifests/{code}/conversions", Map.of("code", code))
            .retrieve()
            .body(Conversion.class);
    }

    /**
     * Forgets the installations' tokens, as of an App the server no longer
     * is.
     */
    void reset() {
        tokens.clear();
    }

    /**
     * The installation of the App on the repository, if it is installed on
     * it.
     */
    Optional<Long> installationOf(final String repository) {
        try {
            return Optional.ofNullable(rest.get()
                    .uri("/repos/{owner}/{repo}/installation", repository(repository))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + key.get().jwt(clock.instant()))
                    .retrieve()
                    .body(Installation.class))
                .map(Installation::id);
        } catch (final HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    /**
     * A token of the installation, valid for a while yet: to fetch its
     * repositories with, as x-access-token.
     */
    String token(final long installation) {
        final var now = clock.instant();

        return tokens.compute(installation, (id, current) ->
            current != null && now.isBefore(current.expiresAt().minus(TOKEN_MARGIN)) ? current : installationToken(id, now)
        ).token();
    }

    private String bearer(final long installation) {
        return "Bearer " + token(installation);
    }

    private InstallationToken installationToken(final long installation, final Instant now) {
        final var token = rest.post()
            .uri("/app/installations/{id}/access_tokens", Map.of("id", installation))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + key.get().jwt(now))
            .retrieve()
            .body(TokenResponse.class);

        if (token == null || token.token() == null) {
            throw new IllegalStateException("GitHub gave no token for installation " + installation);
        }

        return new InstallationToken(token.token(), Instant.parse(token.expires_at()));
    }

    // As GitHub names its fields
    private record TokenResponse(String token, String expires_at) {
    }
}
