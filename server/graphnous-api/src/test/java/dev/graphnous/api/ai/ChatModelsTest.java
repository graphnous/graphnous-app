package dev.graphnous.api.ai;

import dev.graphnous.application.ai.AiApiTokenRetriever;
import dev.graphnous.application.ai.ApiToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatModelsTest {

    @Mock
    private AiApiTokenRetriever tokenRetriever;

    @Test
    void reusesTheModelsOfAKeyWhileItStaysTheSame() {
        final var models = new ChatModels(tokenRetriever);

        when(tokenRetriever.retreive()).thenReturn(new ApiToken("key", "openai"));

        final var chat = models.get("gpt-5");

        assertThat(models.get("gpt-5")).isSameAs(chat);
        assertThat(models.get("gpt-5-mini")).isNotSameAs(chat);
    }

    @Test
    void makesTheModelsAgainForAnotherKey() {
        final var models = new ChatModels(tokenRetriever);

        when(tokenRetriever.retreive()).thenReturn(new ApiToken("key", "openai"));
        final var before = models.get("gpt-5");

        when(tokenRetriever.retreive()).thenReturn(new ApiToken("another key", "openai"));

        assertThat(models.get("gpt-5")).isNotSameAs(before);
    }

    @Test
    void needsAKey() {
        when(tokenRetriever.retreive()).thenReturn(new ApiToken(" ", "openai"));

        assertThatThrownBy(() -> new ChatModels(tokenRetriever).get("gpt-5"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OpenAI API key");
    }

    @Test
    void takesOnlyOpenAi() {
        when(tokenRetriever.retreive()).thenReturn(new ApiToken("key", "mistral"));

        assertThatThrownBy(() -> new ChatModels(tokenRetriever).get("gpt-5"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("'mistral'");
    }
}
