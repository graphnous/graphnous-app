package dev.graphnous.api.scan.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.exception.AuthorizationException;
import dev.graphnous.application.exception.ConflictException;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.scan.DeleteScanCommand;
import dev.graphnous.application.scan.ScanService;
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
import java.util.UUID;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScanControllerTest {

    private static final String OPENAPI_SPEC =
            "../../../graphnous-schemas/openapi/v1.yaml";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanService scanService;

    @MockitoBean
    private RequestContextProvider contextProvider;

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
