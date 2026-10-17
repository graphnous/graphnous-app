package dev.graphnous.github;

import java.time.Instant;

/**
 * The GitHub App that setup registered on GitHub for this server, with its
 * secrets as GitHub gave them.
 *
 * @param slug    its name in GitHub's urls, such as graphnous-acme
 * @param htmlUrl its page on GitHub
 */
record StoredApp(
    String appId,
    String slug,
    String name,
    String htmlUrl,
    String privateKey,
    String webhookSecret,
    Instant createdAt
) {

    /**
     * Leaves the secrets out, so they do not end up in logs.
     */
    @Override
    public String toString() {
        return "StoredApp[appId=" + appId + ", slug=" + slug + ", name=" + name + ", htmlUrl=" + htmlUrl + "]";
    }
}
