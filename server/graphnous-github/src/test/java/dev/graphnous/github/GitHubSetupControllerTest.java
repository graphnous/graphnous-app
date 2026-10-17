package dev.graphnous.github;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.organization.OrganizationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GitHubSetupControllerTest {

    @Mock
    private GitHubAppSetup setup;

    @Mock
    private RequestContextProvider contextProvider;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()), new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        lenient().when(contextProvider.get()).thenReturn(context);
        mvc = MockMvcBuilders.standaloneSetup(new GitHubSetupController(setup, contextProvider)).build();
    }

    @Test
    void saysWhatTheAppIs() throws Exception {
        when(setup.status(context)).thenReturn(new GitHubAppSetup.Status(
            "SETUP", "777", "graphnous-acme", "Graphnous Acme", "https://github.com/apps/graphnous-acme",
            "https://github.com/apps/graphnous-acme/installations/new", null, null, "https://graphnous.acme.test"
        ));

        mvc.perform(get("/api/v1/github/app"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("SETUP"))
            .andExpect(jsonPath("$.slug").value("graphnous-acme"))
            .andExpect(jsonPath("$.installUrl").value("https://github.com/apps/graphnous-acme/installations/new"));
    }

    @Test
    void givesTheManifestToPostToGitHub() throws Exception {
        when(setup.start(context, "Graphnous Acme", "acme-corp", "https://graphnous.acme.test", null)).thenReturn(new GitHubAppSetup.Manifest(
            "https://github.com/organizations/acme-corp/settings/apps/new?state=s1", Map.of("name", "Graphnous Acme")
        ));

        mvc.perform(post("/api/v1/github/app/manifest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Graphnous Acme\", \"organization\": \"acme-corp\", \"serverUrl\": \"https://graphnous.acme.test\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.action").value("https://github.com/organizations/acme-corp/settings/apps/new?state=s1"))
            .andExpect(jsonPath("$.manifest.name").value("Graphnous Acme"));
    }

    @Test
    void sendsTheBrowserOnToInstallTheAppGitHubRegistered() throws Exception {
        when(setup.complete("code-1", "s1")).thenReturn("https://github.com/apps/graphnous-acme/installations/new");

        mvc.perform(get(GitHubSetupController.CALLBACK).queryParam("code", "code-1").queryParam("state", "s1"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://github.com/apps/graphnous-acme/installations/new"));
    }

    @Test
    void saysInWordsWhenTheCallbackIsOfNoSetup() throws Exception {
        when(setup.complete("code-1", "made-up")).thenThrow(new ValidationException("This setup of the GitHub App is not under way here"));

        mvc.perform(get(GitHubSetupController.CALLBACK).queryParam("code", "code-1").queryParam("state", "made-up"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("This setup of the GitHub App is not under way here"));
    }

    @Test
    void forgetsTheApp() throws Exception {
        mvc.perform(delete("/api/v1/github/app"))
            .andExpect(status().isNoContent());

        verify(setup).disconnect(context);
    }
}
