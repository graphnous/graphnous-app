package dev.graphnous.github;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.project.scanner.GitCredentials;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.impact.ImpactAnalysisService;
import dev.graphnous.application.scan.impact.ImpactAssessor;
import dev.graphnous.application.scan.source.ScanSourceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

/**
 * The GitHub App, when graphnous.github.enabled is on: its webhooks at
 * {@link GitHubWebhookController#PATH}, its reports on pull requests, and
 * its setup. Without graphnous.github.app-id, it is the App setup
 * registers, once it has.
 */
@Configuration
@ConditionalOnBooleanProperty("graphnous.github.enabled")
@PropertySource("classpath:graphnous-github.properties")
@EnableConfigurationProperties(GitHubProperties.class)
public class GitHubConfiguration {

    /**
     * Every installation in the server's one organization; a server of many
     * defines its own.
     */
    @Bean
    @ConditionalOnMissingBean
    InstallationOrganizations installationOrganizations(final GitHubProperties properties) {
        final var organization = new OrganizationId(properties.organizationId());

        return installation -> organization;
    }

    /**
     * The App setup registered, with its secrets sealed with the server's
     * secrets key; none can be kept without one.
     */
    @Bean
    StoredApps storedApps(final JdbcClient jdbc, @Value("${graphnous.secrets.key:}") final String secretsKey) {
        return new StoredApps(jdbc, secretsKey == null || secretsKey.isBlank() ? null : SecretBox.of(secretsKey));
    }

    @Bean
    AppIdentities appIdentities(final GitHubProperties properties, final StoredApps storedApps) {
        return new AppIdentities(properties, privateKey(properties), storedApps);
    }

    @Bean
    GitHubClient gitHubClient(final GitHubProperties properties, final AppIdentities identities) {
        return new GitHubClient(
            RestClient.builder(),
            properties.apiUrl(),
            () -> identities.require().key(),
            Clock.systemUTC()
        );
    }

    @Bean
    GitHubAppSetup gitHubAppSetup(
        final GitHubClient github,
        final AppIdentities identities,
        final StoredApps storedApps,
        final AuthorizationService authorizationService,
        final GitHubProperties properties
    ) {
        return new GitHubAppSetup(
            github,
            identities,
            storedApps,
            authorizationService,
            "https://" + GitHubCredentials.webHost(properties.apiUrl()),
            properties.serverUrl(),
            properties.webhookUrl(),
            Clock.systemUTC()
        );
    }

    /**
     * The checkouts of the repositories the App is installed on.
     */
    @Bean
    GitCredentials gitHubCredentials(final GitHubClient github, final AppIdentities identities, final GitHubProperties properties) {
        return new GitHubCredentials(github, identities, properties.apiUrl());
    }

    @Bean
    PullRequestScans pullRequestScans(final JdbcClient jdbc) {
        return new PullRequestScans(jdbc);
    }

    @Bean
    GitHubApp gitHubApp(
        final GitHubClient github,
        final PullRequestScans pullRequestScans,
        final InstallationOrganizations organizations,
        final ScanSourceService scanSourceService,
        final ScanService scanService,
        final ProjectService projectService,
        final ImpactAnalysisService impactAnalysisService,
        final ObjectProvider<ImpactAssessor> assessor,
        final GitHubProperties properties
    ) {
        return new GitHubApp(
            github,
            pullRequestScans,
            organizations,
            scanSourceService,
            scanService,
            projectService,
            impactAnalysisService,
            properties.assess() ? assessor.getIfAvailable() : null,
            properties.scanDefaultBranch(),
            Clock.systemUTC()
        );
    }

    private static String privateKey(final GitHubProperties properties) {
        if (properties.privateKeyFile() == null || properties.privateKeyFile().isBlank()) {
            return properties.privateKey();
        }

        try {
            return Files.readString(Path.of(properties.privateKeyFile()));
        } catch (final IOException e) {
            throw new UncheckedIOException("Reading the GitHub App's private key from " + properties.privateKeyFile() + " failed", e);
        }
    }
}
