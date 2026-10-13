package dev.graphnous.api.chat.v1;

import dev.graphnous.domain.project.Project;
import com.fasterxml.jackson.annotation.JsonRawValue;
import dev.graphnous.application.chat.ChatService;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.domain.chat.ChatThread;
import dev.graphnous.domain.system.System.SystemId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The caller's conversations with the assistant, per system. A thread's id
 * is the AG-UI thread id of its runs at the assistant's endpoint, which keeps
 * the conversation in it.
 * <p>
 * Not in the published OpenAPI spec yet, so its models are its own.
 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final ChatService chatService;
    private final RequestContextProvider contextProvider;

    public ChatController(
        final ChatService chatService,
        final RequestContextProvider contextProvider
    ) {
        this.chatService = chatService;
        this.contextProvider = contextProvider;
    }

    /**
     * A thread as listed: without its messages.
     *
     * @param projectId the project it is about; null when it is about the
     *                  system as a whole
     */
    public record ChatThreadSummary(
        UUID id,
        UUID systemId,
        UUID projectId,
        String title,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    /**
     * @param messages its AG-UI messages, as they were sent
     */
    public record ChatThreadDetails(
        UUID id,
        UUID systemId,
        UUID projectId,
        String title,
        @JsonRawValue String messages,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record ChatThreadPage(
        List<ChatThreadSummary> content,
        int page,
        int size,
        int totalElements,
        int totalPages
    ) {
    }

    /**
     * The caller's threads about the project, the last one talked in first.
     */
    @GetMapping("/projects/{id}/chat/threads")
    public ResponseEntity<ChatThreadPage> getProjectThreads(
        @PathVariable("id") final UUID projectId,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int pageNumber,
        @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) final int size
    ) {
        final Page<ChatThread> page = this.chatService.getProjectThreads(
            this.contextProvider.get(),
            new PageQuery(pageNumber, size, new Sort("updatedAt", Sort.Direction.DESC)),
            new Project.ProjectId(projectId)
        );

        return ResponseEntity.ok(new ChatThreadPage(
            page.content().stream().map(ChatController::summary).toList(),
            page.page(),
            page.size(),
            page.totalElements(),
            page.totalPages()
        ));
    }

    /**
     * Starts a thread about the project.
     */
    @PostMapping("/projects/{id}/chat/threads")
    public ResponseEntity<ChatThreadDetails> createProjectThread(
        @PathVariable("id") final UUID projectId
    ) {
        final var thread = this.chatService.createForProject(this.contextProvider.get(), new Project.ProjectId(projectId));

        return ResponseEntity
            .created(URI.create("/api/v1/chat/threads/" + thread.id().id()))
            .body(details(thread));
    }

    /**
     * The caller's threads about the system as a whole, the last one talked
     * in first; those about one of its projects are that project's.
     */
    @GetMapping("/systems/{id}/chat/threads")
    public ResponseEntity<ChatThreadPage> getThreads(
        @PathVariable("id") final UUID systemId,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int pageNumber,
        @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) final int size
    ) {
        final Page<ChatThread> page = this.chatService.getThreads(
            this.contextProvider.get(),
            new PageQuery(pageNumber, size, new Sort("updatedAt", Sort.Direction.DESC)),
            new SystemId(systemId)
        );

        return ResponseEntity.ok(new ChatThreadPage(
            page.content().stream().map(ChatController::summary).toList(),
            page.page(),
            page.size(),
            page.totalElements(),
            page.totalPages()
        ));
    }

    @PostMapping("/systems/{id}/chat/threads")
    public ResponseEntity<ChatThreadDetails> createThread(
        @PathVariable("id") final UUID systemId
    ) {
        final var thread = this.chatService.create(this.contextProvider.get(), new SystemId(systemId));

        return ResponseEntity
            .created(URI.create("/api/v1/chat/threads/" + thread.id().id()))
            .body(details(thread));
    }

    @GetMapping("/chat/threads/{id}")
    public ResponseEntity<ChatThreadDetails> getThread(
        @PathVariable("id") final UUID id
    ) {
        return ResponseEntity.ok(details(
            this.chatService.getThread(this.contextProvider.get(), new ChatThread.ChatThreadId(id))
        ));
    }

    @DeleteMapping("/chat/threads/{id}")
    public ResponseEntity<Void> deleteThread(
        @PathVariable("id") final UUID id
    ) {
        this.chatService.delete(this.contextProvider.get(), new ChatThread.ChatThreadId(id));

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private static ChatThreadSummary summary(final ChatThread thread) {
        return new ChatThreadSummary(
            thread.id().id(),
            thread.systemId().id(),
            thread.projectId() == null ? null : thread.projectId().id(),
            thread.title(),
            thread.createdAt(),
            thread.updatedAt()
        );
    }

    private static ChatThreadDetails details(final ChatThread thread) {
        return new ChatThreadDetails(
            thread.id().id(),
            thread.systemId().id(),
            thread.projectId() == null ? null : thread.projectId().id(),
            thread.title(),
            thread.messages(),
            thread.createdAt(),
            thread.updatedAt()
        );
    }
}
