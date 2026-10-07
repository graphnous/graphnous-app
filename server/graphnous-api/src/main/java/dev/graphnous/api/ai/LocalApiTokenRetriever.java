package dev.graphnous.api.ai;

import dev.graphnous.application.ai.AiApiTokenRetriever;
import dev.graphnous.application.ai.ApiToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalApiTokenRetriever implements AiApiTokenRetriever {

    @Value("${graphnous.ai.openai.api-key:}")
    private String apiKey;

    @Override
    public ApiToken retreive() {
        return new ApiToken(apiKey, "openai");
    }

}
