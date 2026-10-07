package dev.graphnous.api.ai;

import dev.graphnous.application.context.RequestContextProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

/**
 * The assistant is served over AG-UI, also without an API key, when it
 * answers with an error.
 */
@SpringBootTest(properties = "graphnous.ai.openai.api-key=")
@AutoConfigureMockMvc
class AgentEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestContextProvider contextProvider;

    @Test
    void reportsARunWithoutAnApiKeyAsAnError() throws Exception {
        final var started = mockMvc.perform(
                post("/agent")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .content("""
                        {"threadId": "thread", "runId": "run", "messages": [
                          {"id": "1", "role": "user", "content": "Which systems do I have?"}
                        ]}
                        """)
            )
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("RUN_ERROR")))
            .andExpect(content().string(containsString("OpenAI API key")));
    }
}
