package dev.graphnous.api.project.scanner.docker;

import dev.graphnous.application.project.scanner.GitCredentials;
import dev.graphnous.application.project.scanner.ScanLogger;
import dev.graphnous.application.ssh.SshKeyRetriever;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.log.ScanLog.ScanLogLevel;
import dev.graphnous.scanner.docker.DockerGitCheckout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockerSourceCheckoutTest {

    private static final String KNOWN_HOSTS = "github.com ssh-ed25519 AAAA";

    @Mock
    private DockerGitCheckout gitCheckout;

    @Mock
    private DockerScanContainers containers;

    @Mock
    private SshKeyRetriever sshKeyRetriever;

    @Mock
    private ScanLogger logger;

    private final Scan.ScanId scanId = Scan.ScanId.generate();
    private final Scan.SourceRevision revision = Scan.SourceRevision.requested("abc123", "orders-v2");

    @BeforeEach
    void setUp() {
        when(containers.labels(scanId)).thenReturn(Map.of());
        when(gitCheckout.checkout(eq(scanId.id().toString()), any(), any(), any()))
            .thenReturn(new DockerGitCheckout.Checkout(Path.of("/checkouts", scanId.id().toString()), "a".repeat(40)));
    }

    @Test
    void fetchesWithTheCredentialsGivenForTheRepository() {
        final GitCredentials credentials = gitUrl -> Optional.of(new GitCredentials.Credential(
            "https://github.com/acme/shop.git", "x-access-token", "ghs_secret", "the GitHub App"
        ));

        final var checkedOut = checkout(credentials).checkout(scanId, "git@github.com:acme/shop.git", revision, logger);

        assertThat(checkedOut.revision()).isEqualTo("a".repeat(40));
        verify(gitCheckout).checkout(eq(scanId.id().toString()), eq(new DockerGitCheckout.GitSource(
            "https://github.com/acme/shop.git", "orders-v2", "abc123", null, null, "x-access-token", "ghs_secret"
        )), any(), any());
        verify(logger).log(ScanLogLevel.INFO, "Fetching https://github.com/acme/shop.git with the access of the GitHub App");
        verify(logger, never()).log(any(), argThat(message -> message.contains("ghs_secret")));
        verify(sshKeyRetriever, never()).retrieve();
    }

    @Test
    void fetchesWithTheSshKeyWithoutCredentials() {
        when(sshKeyRetriever.retrieve()).thenReturn("ssh key");

        checkout(GitCredentials.NONE).checkout(scanId, "git@github.com:acme/shop.git", revision, logger);

        verify(gitCheckout).checkout(eq(scanId.id().toString()), eq(new DockerGitCheckout.GitSource(
            "git@github.com:acme/shop.git", "orders-v2", "abc123", "ssh key", KNOWN_HOSTS
        )), any(), any());
        verify(logger, never()).log(any(), anyString());
    }

    private DockerSourceCheckout checkout(final GitCredentials credentials) {
        return new DockerSourceCheckout(gitCheckout, containers, sshKeyRetriever, KNOWN_HOSTS, credentials);
    }
}
