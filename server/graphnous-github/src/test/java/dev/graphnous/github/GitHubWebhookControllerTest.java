package dev.graphnous.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GitHubWebhookControllerTest {

    private static final String SECRET = "webhook secret";
    private static final String BODY = "{\"action\": \"opened\", \"pull_request\": {\"number\": 3}}";

    @Mock
    private GitHubApp app;

    @Mock
    private AppIdentities identities;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
            .standaloneSetup(new GitHubWebhookController(app, identities, JsonMapper.builder().build()))
            .build();
    }

    @Test
    void handlesADeliverySignedWithTheSecret() throws Exception {
        setUp(SECRET);

        mvc.perform(post(GitHubWebhookController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", "pull_request")
                .header("X-Hub-Signature-256", signature(SECRET))
                .content(BODY))
            .andExpect(status().isAccepted());

        final var payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(app).handle(eq("pull_request"), payload.capture());
        assertThat(payload.getValue().path("pull_request").path("number").asInt()).isEqualTo(3);
    }

    @Test
    void refusesADeliveryNotSignedWithTheSecret() throws Exception {
        setUp(SECRET);

        mvc.perform(post(GitHubWebhookController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", "pull_request")
                .header("X-Hub-Signature-256", signature("another secret"))
                .content(BODY))
            .andExpect(status().isUnauthorized());

        mvc.perform(post(GitHubWebhookController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", "pull_request")
                .content(BODY))
            .andExpect(status().isUnauthorized());

        verify(app, never()).handle(anyString(), any());
    }

    @Test
    void handlesNoDeliveryBeforeTheAppIsSetUp() throws Exception {
        when(identities.current()).thenReturn(Optional.empty());

        mvc.perform(post(GitHubWebhookController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", "pull_request")
                .header("X-Hub-Signature-256", signature(SECRET))
                .content(BODY))
            .andExpect(status().isServiceUnavailable());

        verify(app, never()).handle(anyString(), any());
    }

    private void setUp(final String secret) {
        when(identities.current()).thenReturn(Optional.of(new AppIdentities.Identity(
            "12345", null, null, null, null, secret, AppIdentities.Source.CONFIGURATION
        )));
    }

    private static String signature(final String secret) {
        return WebhookSignature.PREFIX
            + HexFormat.of().formatHex(WebhookSignature.sign(secret, BODY.getBytes(StandardCharsets.UTF_8)));
    }
}
