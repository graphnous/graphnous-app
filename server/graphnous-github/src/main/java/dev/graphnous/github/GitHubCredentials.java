package dev.graphnous.github;

import dev.graphnous.application.project.GitRepositories;
import dev.graphnous.application.project.scanner.GitCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * Access to the repositories the App is installed on, so the server
 * checks them out without access of its own: over https, with a token of
 * the installation, which the App's Contents permission lets read them.
 */
class GitHubCredentials implements GitCredentials {

    static final String USERNAME = "x-access-token";

    private static final Logger log = LoggerFactory.getLogger(GitHubCredentials.class);

    private final GitHubClient github;
    private final AppIdentities identities;
    private final String host;

    /**
     * @param apiUrl GitHub's API, whose web host the repositories are on:
     *               github.com for api.github.com, the server itself for a
     *               GitHub Enterprise Server
     */
    GitHubCredentials(final GitHubClient github, final AppIdentities identities, final String apiUrl) {
        this.github = github;
        this.identities = identities;
        this.host = webHost(apiUrl);
    }

    @Override
    public Optional<Credential> forRepository(final String gitUrl) {
        if (gitUrl == null || gitUrl.isBlank() || identities.current().isEmpty()) {
            return Optional.empty();
        }

        final var key = GitRepositories.key(gitUrl);

        if (!key.startsWith(host + "/")) {
            return Optional.empty();
        }

        final var repository = key.substring(host.length() + 1);

        // owner/name, and nothing else
        if (repository.indexOf('/') < 1 || repository.indexOf('/') != repository.lastIndexOf('/')) {
            return Optional.empty();
        }

        try {
            return github.installationOf(repository).map(installation -> new Credential(
                "https://" + host + "/" + repository + ".git",
                USERNAME,
                github.token(installation),
                "the GitHub App"
            ));
        } catch (final RuntimeException e) {
            // The server's own access may still do
            log.warn("Getting the GitHub App's access to {} failed", repository, e);
            return Optional.empty();
        }
    }

    static String webHost(final String apiUrl) {
        final var apiHost = URI.create(apiUrl).getHost().toLowerCase(Locale.ROOT);

        return apiHost.equals("api.github.com") ? "github.com" : apiHost;
    }
}
