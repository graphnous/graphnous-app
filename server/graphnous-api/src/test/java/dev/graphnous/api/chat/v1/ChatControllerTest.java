package dev.graphnous.api.chat.v1;

import dev.graphnous.application.chat.ChatService;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.domain.chat.ChatThread;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private RequestContextProvider contextProvider;

    private final System.SystemId systemId = System.SystemId.generate();

    private final ChatThread thread = new ChatThread(
        ChatThread.ChatThreadId.generate(),
        systemId,
        null,
        "Which systems do I have?",
        "[{\"id\":\"1\",\"role\":\"user\",\"content\":\"Which systems do I have?\"}]",
        NOW,
        NOW
    );

    @Test
    void listsTheThreadsWithoutTheirMessages() throws Exception {
        when(chatService.getThreads(any(), any(), eq(systemId))).thenReturn(new Page<>(List.of(thread), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/systems/{id}/chat/threads", systemId.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(thread.id().id().toString()))
            .andExpect(jsonPath("$.content[0].title").value("Which systems do I have?"))
            .andExpect(jsonPath("$.content[0].messages").doesNotExist())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createsAThread() throws Exception {
        when(chatService.create(any(), eq(systemId))).thenReturn(thread);

        mockMvc.perform(post("/api/v1/systems/{id}/chat/threads", systemId.id()))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/chat/threads/" + thread.id().id()))
            .andExpect(jsonPath("$.systemId").value(systemId.id().toString()));
    }

    @Test
    void returnsTheMessagesAsTheyWereSent() throws Exception {
        when(chatService.getThread(any(), eq(thread.id()))).thenReturn(thread);

        mockMvc.perform(get("/api/v1/chat/threads/{id}", thread.id().id()))
            .andExpect(status().isOk())
            // An array, not a string holding one
            .andExpect(jsonPath("$.messages[0].role").value("user"))
            .andExpect(jsonPath("$.messages[0].content").value("Which systems do I have?"));
    }

    @Test
    void answersNotFoundForAThreadOfAnotherUser() throws Exception {
        when(chatService.getThread(any(), any())).thenThrow(new NotFoundException("Not found"));

        mockMvc.perform(get("/api/v1/chat/threads/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deletesAThread() throws Exception {
        mockMvc.perform(delete("/api/v1/chat/threads/{id}", thread.id().id()))
            .andExpect(status().isNoContent());

        verify(chatService).delete(any(), eq(thread.id()));
    }
}
