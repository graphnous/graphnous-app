package dev.graphnous.api.scan.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.enhancer.ScanEnhancementService;
import dev.graphnous.application.exception.AuthorizationException;
import dev.graphnous.application.exception.ConflictException;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.scan.DeleteScanCommand;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.ScanStep;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScanControllerTest {

    private static final String OPENAPI_SPEC =
            "https://graphnous.github.io/graphnous-schemas/openapi/v1.yaml";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanService scanService;

    @MockitoBean
    private RequestContextProvider contextProvider;

    @MockitoBean
    private ScanEnhancementService scanEnhancementService;

    // Not in the published OpenAPI spec yet, so not validated against it

    @Test
    void getWhatAScanWasCreatedWith() throws Exception {
        final var now = Instant.now();
        final var scan = new Scan(
            Scan.ScanId.generate(), Project.ProjectId.generate(), Scan.ScanStatus.COMPLETED,
            new Scan.SourceRevision("abc123", "main"), now, now, now,
            Set.of("spring", "react"), List.of("*.spec.ts", "generated/"), Set.of("TYPESCRIPT", "JAVA")
        );

        when(scanService.getScan(any(), eq(scan.id()))).thenReturn(scan);

        mockMvc.perform(get("/api/v1/scans/{id}/options", scan.id().id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enhancers[0]").value("react"))
            .andExpect(jsonPath("$.enhancers[1]").value("spring"))
            .andExpect(jsonPath("$.excludes[0]").value("*.spec.ts"))
            .andExpect(jsonPath("$.excludes[1]").value("generated/"))
            .andExpect(jsonPath("$.languages[0]").value("JAVA"))
            .andExpect(jsonPath("$.languages[1]").value("TYPESCRIPT"));
    }

    @Test
    void getNoChoicesForAScanCreatedWithoutAny() throws Exception {
        final var scan = completedScan();

        when(scanService.getScan(any(), eq(scan.id()))).thenReturn(scan);

        mockMvc.perform(get("/api/v1/scans/{id}/options", scan.id().id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enhancers").isEmpty())
            .andExpect(jsonPath("$.excludes").isEmpty())
            .andExpect(jsonPath("$.languages").isEmpty());
    }

    @Test
    void enhanceScanWithEveryEnhancer() throws Exception {
        final var scan = completedScan();

        when(scanEnhancementService.enhance(any(), eq(scan.id()), isNull(), eq(false))).thenReturn(scan);

        mockMvc.perform(post("/api/v1/scans/{id}/enhancements", scan.id().id()))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", endsWith("/api/v1/scans/" + scan.id().id() + "/steps")))
            .andExpect(jsonPath("$.id").value(scan.id().id().toString()));
    }

    @Test
    void enhanceScanWithTheEnhancersAskedFor() throws Exception {
        final var scan = completedScan();

        when(scanEnhancementService.enhance(any(), eq(scan.id()), eq(Set.of("react", "nextjs")), eq(false))).thenReturn(scan);

        mockMvc.perform(
            post("/api/v1/scans/{id}/enhancements", scan.id().id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enhancers\": [\"react\", \"nextjs\"]}")
        )
            .andExpect(status().isAccepted());
    }

    @Test
    void enhanceScanWithNoEnhancers() throws Exception {
        final var scan = completedScan();

        when(scanEnhancementService.enhance(any(), eq(scan.id()), eq(Set.of()), eq(false))).thenReturn(scan);

        mockMvc.perform(
            post("/api/v1/scans/{id}/enhancements", scan.id().id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enhancers\": []}")
        )
            .andExpect(status().isAccepted());

        // Not every enhancer, as without a choice
        verify(scanEnhancementService).enhance(any(), eq(scan.id()), eq(Set.of()), eq(false));
    }

    @Test
    void enhanceScanAfterStoringItsResultsAgain() throws Exception {
        final var scan = completedScan();

        when(scanEnhancementService.enhance(any(), eq(scan.id()), isNull(), eq(true))).thenReturn(scan);

        mockMvc.perform(
            post("/api/v1/scans/{id}/enhancements", scan.id().id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"restore\": true}")
        )
            .andExpect(status().isAccepted());
    }

    @Test
    void enhanceScanThatCannotBeEnhanced() throws Exception {
        when(scanEnhancementService.enhance(any(), any(), any(), anyBoolean()))
            .thenThrow(new ConflictException("Scan kept no results to enhance"));

        mockMvc.perform(post("/api/v1/scans/{id}/enhancements", UUID.randomUUID()))
            .andExpect(status().isConflict());
    }

    private static Scan completedScan() {
        final var now = Instant.now();

        return new Scan(
            Scan.ScanId.generate(), Project.ProjectId.generate(), Scan.ScanStatus.COMPLETED,
            new Scan.SourceRevision("4f2a9c1e88d0a19c3e7f0b42c0ffee1234567890", "main", "v1.2.0"), now, now, now
        );
    }

    @Test
    void getScan() throws Exception {
        final var now = Instant.now();
        final var scan = new Scan(
            Scan.ScanId.generate(), Project.ProjectId.generate(), Scan.ScanStatus.COMPLETED,
            new Scan.SourceRevision("4f2a9c1e88d0a19c3e7f0b42c0ffee1234567890", "main", "v1.2.0"), now, now, now
        );

        when(scanService.getScan(any(), eq(scan.id()))).thenReturn(scan);

        mockMvc.perform(get("/api/v1/scans/{id}", scan.id().id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(scan.id().id().toString()))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.revision").value("4f2a9c1e88d0a19c3e7f0b42c0ffee1234567890"))
            .andExpect(jsonPath("$.requestedRevision").value("v1.2.0"))
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void getUnknownScan() throws Exception {
        when(scanService.getScan(any(), any())).thenThrow(new NotFoundException("Scan not found"));

        mockMvc.perform(get("/api/v1/scans/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void getScanWithoutPermission() throws Exception {
        when(scanService.getScan(any(), any())).thenThrow(new AuthorizationException("Not allowed"));

        mockMvc.perform(get("/api/v1/scans/{id}", UUID.randomUUID()))
            .andExpect(status().isForbidden())
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void getScanWithAnInvalidId() throws Exception {
        mockMvc.perform(get("/api/v1/scans/not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void deleteScan() throws Exception {
        final var scanId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/scans/{id}", scanId))
            .andExpect(status().isNoContent())
            .andExpect(openApi().isValid(OPENAPI_SPEC));

        verify(scanService).delete(any(), any(DeleteScanCommand.class));
    }

    @Test
    void deleteUnknownScan() throws Exception {
        doThrow(new NotFoundException("Scan not found"))
            .when(scanService).delete(any(), any());

        mockMvc.perform(delete("/api/v1/scans/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void deleteActiveScan() throws Exception {
        doThrow(new ConflictException("Scan is still running"))
            .when(scanService).delete(any(), any());

        mockMvc.perform(delete("/api/v1/scans/{id}", UUID.randomUUID()))
            .andExpect(status().isConflict())
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void deleteScanWithoutPermission() throws Exception {
        doThrow(new AuthorizationException("Not allowed"))
            .when(scanService).delete(any(), any());

        mockMvc.perform(delete("/api/v1/scans/{id}", UUID.randomUUID()))
            .andExpect(status().isForbidden())
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void getScanLogs() throws Exception {
        final var scanId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/scans/{id}/logs", scanId))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OPENAPI_SPEC));

        // The logs are only read after checking the caller may read the scan
        verify(scanService).getScan(any(), eq(new Scan.ScanId(scanId)));
    }

    @Test
    void getLogsOfUnknownScan() throws Exception {
        when(scanService.getScan(any(), any())).thenThrow(new NotFoundException("Scan not found"));

        mockMvc.perform(get("/api/v1/scans/{id}/logs", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void getScanLogsWithoutPermission() throws Exception {
        when(scanService.getScan(any(), any())).thenThrow(new AuthorizationException("Not allowed"));

        mockMvc.perform(get("/api/v1/scans/{id}/logs", UUID.randomUUID()))
            .andExpect(status().isForbidden())
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void streamLogsOfUnknownScan() throws Exception {
        when(scanService.getScan(any(), any())).thenThrow(new NotFoundException("Scan not found"));

        mockMvc.perform(
            get("/api/v1/scans/{id}/logs/stream", UUID.randomUUID())
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getScanLogsWithAnInvalidPageSize() throws Exception {
        mockMvc.perform(
            get("/api/v1/scans/{id}/logs", UUID.randomUUID())
                .param("size", "1001")
        )
            .andExpect(status().isBadRequest());
    }

    @Test
    void getScanExecution() throws Exception {
        final var scanId = new Scan.ScanId(UUID.randomUUID());
        final var started = Instant.parse("2026-09-30T10:00:00Z");

        when(scanService.getSteps(any(), eq(scanId))).thenReturn(List.of(
            ScanStep.pending(scanId, ScanStep.ScanStepType.CHECKOUT).start(started).complete(started.plusSeconds(2)),
            ScanStep.pending(scanId, ScanStep.ScanStepType.PLAN).start(started.plusSeconds(2)).fail(started.plusSeconds(3), "No build file"),
            ScanStep.pending(scanId, ScanStep.ScanStepType.SCAN).skip(started.plusSeconds(3))
        ));

        mockMvc.perform(get("/api/v1/scans/{id}/steps", scanId.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scanId").value(scanId.id().toString()))
            .andExpect(jsonPath("$.steps[0].type").value("CHECKOUT"))
            .andExpect(jsonPath("$.steps[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.steps[1].status").value("FAILED"))
            .andExpect(jsonPath("$.steps[1].error").value("No build file"))
            .andExpect(jsonPath("$.steps[2].status").value("SKIPPED"))
            .andExpect(jsonPath("$.steps[2].startedAt").isEmpty())
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void getExecutionOfUnknownScan() throws Exception {
        when(scanService.getSteps(any(), any())).thenThrow(new NotFoundException("Scan not found"));

        mockMvc.perform(get("/api/v1/scans/{id}/steps", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }
}
