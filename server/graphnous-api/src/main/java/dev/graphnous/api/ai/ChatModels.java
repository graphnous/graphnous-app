package dev.graphnous.api.ai;

import dev.graphnous.application.ai.AiApiTokenRetriever;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * The chat models the agents run on, by name, with the API key the
 * {@link AiApiTokenRetriever} gives each time: a changed key is used
 * without a restart, and the models of the last key are reused while it
 * stays the same.
 */
public class ChatModels {

    private final AiApiTokenRetriever tokenRetriever;

    // The key the models were made with
    private String key;
    private final Map<String, ChatModel> models = new HashMap<>();

    public ChatModels(final AiApiTokenRetriever tokenRetriever) {
        this.tokenRetriever = tokenRetriever;
    }

    /**
     * @throws IllegalStateException when there is no API key, or it is of a
     *                               provider other than OpenAI
     */
    public synchronized ChatModel get(final String model) {
        final var token = tokenRetriever.retreive();

        if (token == null || token.token() == null || token.token().isBlank()) {
            throw new IllegalStateException(
                "The assistant is not configured: set an OpenAI API key (graphnous.ai.openai.api-key)"
            );
        }

        if (!"openai".equalsIgnoreCase(token.provider())) {
            throw new IllegalStateException("The assistant does not support AI provider '" + token.provider() + "'");
        }

        if (!token.token().equals(key)) {
            models.clear();
            key = token.token();
        }

        return models.computeIfAbsent(model, name -> OpenAiChatModel.builder()
            .options(OpenAiChatOptions.builder()
                .apiKey(token.token())
                .model(name)
                .build())
            .build());
    }
}
