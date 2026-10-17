package dev.graphnous.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

/**
 * The GitHub App this server is: as registered on GitHub, under the App's
 * settings; or else, without an App ID, the App setup registers.
 *
 * @param appId             its App ID
 * @param privateKey        one of its private keys, in PEM, as GitHub
 *                          gives it
 * @param privateKeyFile    or the file that holds it
 * @param webhookSecret     the secret its webhooks are signed with
 * @param apiUrl            GitHub's API, such as that of a GitHub
 *                          Enterprise Server
 * @param organizationId    the organization it works in, for every
 *                          installation
 * @param scanDefaultBranch whether a push to a repository's default branch
 *                          scans it
 * @param assess            whether the impact agent heads each report with
 *                          its assessment, which needs a model
 * @param serverUrl         where GitHub reaches this server, for the
 *                          webhooks of an App setup registers
 * @param webhookUrl        where GitHub delivers that App's webhooks, when
 *                          not to the server itself, such as a smee.io
 *                          channel that forwards them to a server on a
 *                          laptop
 */
@ConfigurationProperties("graphnous.github")
public record GitHubProperties(
    boolean enabled,
    String appId,
    String privateKey,
    String privateKeyFile,
    String webhookSecret,
    String apiUrl,
    UUID organizationId,
    boolean scanDefaultBranch,
    boolean assess,
    String serverUrl,
    String webhookUrl
) {
}
