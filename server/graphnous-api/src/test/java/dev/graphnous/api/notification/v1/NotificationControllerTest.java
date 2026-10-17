package dev.graphnous.api.notification.v1;

import dev.graphnous.api.OpenApiSpec;
import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.notification.NotificationEvent;
import dev.graphnous.application.notification.NotificationPublisher;
import dev.graphnous.application.notification.NotificationService;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.notification.Notification;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTest {

    private static final String OPENAPI_SPEC = OpenApiSpec.LOCATION;
    private static final Instant NOW = Instant.parse("2026-10-18T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationPublisher publisher;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private RequestContextProvider contextProvider;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()), new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final System.SystemId systemId = System.SystemId.generate();
    private final Project.ProjectId projectId = Project.ProjectId.generate();
    private final Scan.ScanId scanId = Scan.ScanId.generate();

    private final Notification notification = new Notification(
        Notification.NotificationId.generate(), systemId, projectId, scanId, "Scan failed", "The checkout failed", false, NOW
    );

    @BeforeEach
    void setUp() {
        when(contextProvider.get()).thenReturn(context);
    }

    @Test
    void listsTheUnreadNotificationsOfASystemNewestFirst() throws Exception {
        when(notificationService.getNotifications(eq(context), any(PageQuery.class), eq(systemId), eq(true)))
            .thenReturn(new Page<>(List.of(notification), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/systems/{id}/notifications", systemId.id()).queryParam("unread", "true"))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OPENAPI_SPEC))
            .andExpect(jsonPath("$.content[0].id").value(notification.id().id().toString()))
            .andExpect(jsonPath("$.content[0].scanId").value(scanId.id().toString()))
            .andExpect(jsonPath("$.content[0].read").value(false))
            .andExpect(jsonPath("$.totalElements").value(1));

        verify(notificationService).getNotifications(context, new PageQuery(0, 20, NotificationController.NEWEST_FIRST), systemId, true);
    }

    @Test
    void listsTheNotificationsOfAProjectAndAScan() throws Exception {
        when(notificationService.getNotifications(eq(context), any(PageQuery.class), eq(projectId), eq(false)))
            .thenReturn(new Page<>(List.of(notification), 0, 20, 1, 1));
        when(notificationService.getNotifications(eq(context), any(PageQuery.class), eq(scanId), eq(false)))
            .thenReturn(new Page<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/projects/{id}/notifications", projectId.id()))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OPENAPI_SPEC))
            .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get("/api/v1/scans/{id}/notifications", scanId.id()).queryParam("size", "5"))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OPENAPI_SPEC))
            .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void refusesAPageSizeOverTheMost() throws Exception {
        mockMvc.perform(get("/api/v1/systems/{id}/notifications", systemId.id()).queryParam("size", "101"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void countsAndMarksReadTheUnreadNotificationsOfASystem() throws Exception {
        when(notificationService.countUnread(context, systemId)).thenReturn(4L);
        when(notificationService.markAllRead(context, systemId)).thenReturn(4);

        mockMvc.perform(get("/api/v1/systems/{id}/notifications/unread", systemId.id()))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OPENAPI_SPEC))
            .andExpect(jsonPath("$.count").value(4));
        mockMvc.perform(post("/api/v1/systems/{id}/notifications/read", systemId.id()))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OPENAPI_SPEC))
            .andExpect(jsonPath("$.count").value(4));
    }

    @Test
    void getsMarksAndDeletesANotification() throws Exception {
        when(notificationService.getNotification(context, notification.id())).thenReturn(notification);
        when(notificationService.markRead(context, notification.id(), true)).thenReturn(notification.withRead(true));

        mockMvc.perform(get("/api/v1/notifications/{id}", notification.id().id()))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OPENAPI_SPEC))
            .andExpect(jsonPath("$.title").value("Scan failed"));
        mockMvc.perform(patch("/api/v1/notifications/{id}", notification.id().id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"read\": true}"))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OPENAPI_SPEC))
            .andExpect(jsonPath("$.read").value(true));
        mockMvc.perform(delete("/api/v1/notifications/{id}", notification.id().id()))
            .andExpect(status().isNoContent());

        verify(notificationService).delete(context, notification.id());
    }

    @Test
    void needsReadToMarkANotification() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/{id}", notification.id().id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void streamsWhatHappensToTheNotificationsOfTheCallersSystem() throws Exception {
        final var result = mockMvc.perform(get("/api/v1/systems/{id}/notifications/stream", systemId.id())
                .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andReturn();

        publisher.publish(NotificationEvent.created(notification));
        // Of another user's reading, and of another system: not for this stream
        publisher.publish(NotificationEvent.allRead(systemId, new UserContext(UUID.randomUUID())));
        publisher.publish(NotificationEvent.allRead(System.SystemId.generate(), context.user()));
        publisher.publish(NotificationEvent.allRead(systemId, context.user()));

        final var body = result.getResponse().getContentAsString();

        assertThat(body).startsWith(":notifications of system " + systemId.id());
        assertThat(body).contains("event:created\ndata:{\"type\":\"CREATED\",\"systemId\":\"" + systemId.id()
            + "\",\"notificationId\":\"" + notification.id().id() + "\",\"notification\":{");
        assertThat(body).contains("event:all_read\ndata:{\"type\":\"ALL_READ\",\"systemId\":\"" + systemId.id() + "\"}");
        assertThat(body.split("event:all_read", -1)).hasSize(2);
    }

    @Test
    void streamsNothingOfASystemTheCallerMayNotRead() throws Exception {
        org.mockito.Mockito.doThrow(new NotFoundException("No such system"))
            .when(notificationService).requireReadable(context, systemId);

        mockMvc.perform(get("/api/v1/systems/{id}/notifications/stream", systemId.id())
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }
}
