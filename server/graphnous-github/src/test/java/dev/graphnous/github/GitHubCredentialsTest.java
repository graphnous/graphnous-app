package dev.graphnous.github;

import dev.graphnous.application.project.scanner.GitCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubCredentialsTest {

    @Mock
    private GitHubClient github;

    @Mock
    private AppIdentities identities;

    @BeforeEach
    void setUp() {
        lenient().when(identities.current()).thenReturn(Optional.of(new AppIdentities.Identity(
            "12345", null, null, null, null, "secret", AppIdentities.Source.CONFIGURATION
        )));
    }

    @ParameterizedTest
    @ValueSource(strings = {"git@github.com:acme/shop.git", "https://github.com/Acme/Shop", "ssh://git@github.com/acme/shop.git"})
    void fetchesARepositoryTheAppIsInstalledOnWithItsToken(final String gitUrl) {
        when(github.installationOf("acme/shop")).thenReturn(Optional.of(42L));
        when(github.token(42)).thenReturn("ghs_token");

        assertThat(credentials("https://api.github.com").forRepository(gitUrl)).contains(new GitCredentials.Credential(
            "https://github.com/acme/shop.git", "x-access-token", "ghs_token", "the GitHub App"
        ));
    }

    @Test
    void leavesRepositoriesTheAppIsNotInstalledOn() {
        when(github.installationOf("acme/blog")).thenReturn(Optional.empty());

        assertThat(credentials("https://api.github.com").forRepository("https://github.com/acme/blog")).isEmpty();
    }

    @Test
    void leavesRepositoriesOfOtherHosts() {
        final var credentials = credentials("https://api.github.com");

        assertThat(credentials.forRepository("https://gitlab.com/acme/shop.git")).isEmpty();
        assertThat(credentials.forRepository("https://github.com/acme")).isEmpty();
        assertThat(credentials.forRepository("https://github.com/acme/shop/tree/main")).isEmpty();
        assertThat(credentials.forRepository(" ")).isEmpty();
        verify(github, never()).installationOf(anyString());
    }

    @Test
    void takesTheRepositoriesOfAGitHubEnterpriseServer() {
        when(github.installationOf("acme/shop")).thenReturn(Optional.of(7L));
        when(github.token(7)).thenReturn("ghs_token");

        assertThat(credentials("https://github.acme.test/api/v3").forRepository("git@github.acme.test:acme/shop.git"))
            .map(GitCredentials.Credential::url)
            .contains("https://github.acme.test/acme/shop.git");
    }

    @Test
    void givesNoAccessBeforeTheAppIsSetUp() {
        when(identities.current()).thenReturn(Optional.empty());

        assertThat(credentials("https://api.github.com").forRepository("https://github.com/acme/shop")).isEmpty();
        verify(github, never()).installationOf(anyString());
    }

    @Test
    void fallsBackToTheServersOwnAccessWhenGitHubFails() {
        when(github.installationOf("acme/shop")).thenThrow(new IllegalStateException("GitHub unavailable"));

        assertThat(credentials("https://api.github.com").forRepository("https://github.com/acme/shop")).isEmpty();
    }

    private GitHubCredentials credentials(final String apiUrl) {
        return new GitHubCredentials(github, identities, apiUrl);
    }
}
