package dev.graphnous.api.project.v1;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.jayway.jsonpath.JsonPath;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.exception.ConflictException;
import dev.graphnous.application.exception.EntitlementException;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.project.DeleteProjectCommand;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.project.UpdateProjectCommand;
import dev.graphnous.application.scan.CreateScanCommand;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.UploadScanCommand;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerTest {

    private static final String OPENAPI_SPEC =
            "../../../graphnous-schemas/openapi/v1.yaml";

    /**
     * Checks only the response, for requests that are invalid on purpose.
     */
    private static final OpenApiInteractionValidator RESPONSE_VALIDATOR =
        OpenApiInteractionValidator.createForSpecificationUrl(OPENAPI_SPEC)
            .withLevelResolver(
                LevelResolver.create()
                    .withLevel("validation.request", ValidationReport.Level.IGNORE)
                    .build()
            )
            .build();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private ScanService scanService;

    @MockitoBean
    private RequestContextProvider contextProvider;

    @Test
    void getScansNewestFirst() throws Exception {
        when(scanService.getScans(
            any(),
            argThat((PageQuery query) ->
                query.sort().property().equals("createdAt")
                    && query.sort().direction() == Sort.Direction.DESC
            ),
            any()
        )).thenReturn(new Page<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(
            get("/api/v1/projects/{id}/scans", UUID.randomUUID())
                .param("sort", "createdAt")
                .param("direction", "desc")
        )
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void getScansWithInvalidPaging() throws Exception {
        for (final var param : List.of(
            new String[] {"size", "101"},
            new String[] {"sort", "name"},
            new String[] {"direction", "sideways"}
        )) {
            mockMvc.perform(
                get("/api/v1/projects/{id}/scans", UUID.randomUUID())
                    .param(param[0], param[1])
            )
            .andExpect(status().isBadRequest())
            .andExpect(openApi().isValid(RESPONSE_VALIDATOR));
        }
    }

    @Test
    void deleteProjectWithActiveScans() throws Exception {
        final var projectId = UUID.randomUUID();

        doThrow(new ConflictException("The project has active scans"))
            .when(projectService).delete(any(), any());

        mockMvc.perform(delete("/api/v1/projects/{id}", projectId))
            .andExpect(status().isConflict())
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void getProject() throws Exception {
        final var project = project();

        when(projectService.getProject(any(), eq(project.id()))).thenReturn(project);

        mockMvc.perform(get("/api/v1/projects/{id}", project.id().id()))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void updateProject() throws Exception {
        final var project = project();

        when(projectService.update(any(), any(UpdateProjectCommand.class))).thenReturn(project);

        mockMvc.perform(
            put("/api/v1/projects/{id}", project.id().id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"backend\", \"path\": \"api\", \"systemId\": \"" + project.systemId().id() + "\"}")
        )
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void deleteProject() throws Exception {
        final var id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/projects/{id}", id))
            .andExpect(status().isNoContent())
            .andExpect(openApi().isValid(OPENAPI_SPEC));

        verify(projectService).delete(any(), eq(new DeleteProjectCommand(new Project.ProjectId(id))));
    }

    @Test
    void createScan() throws Exception {
        final var projectId = Project.ProjectId.generate();
        final var now = Instant.now();
        final var scan = new Scan(
            Scan.ScanId.generate(), projectId, Scan.ScanStatus.PENDING,
            new Scan.SourceRevision("abc123", "main"), now, now, null
        );

        when(scanService.create(any(), any(CreateScanCommand.class))).thenReturn(scan);

        mockMvc.perform(
            post("/api/v1/projects/{id}/scans", projectId.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branch\": \"main\", \"revision\": \"abc123\"}")
        )
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "http://localhost/api/v1/scans/" + scan.id().id() + "/steps"))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.startedAt").isEmpty())
        .andExpect(jsonPath("$.completedAt").isEmpty())
        .andExpect(openApi().isValid(OPENAPI_SPEC));

        verify(scanService).create(any(), eq(new CreateScanCommand(projectId, "abc123", "main")));
    }

    @Test
    void listsAFinishedScanWithWhenItCompleted() throws Exception {
        final var projectId = Project.ProjectId.generate();
        final var created = Instant.parse("2026-09-30T10:00:00Z");
        final var started = Instant.parse("2026-09-30T10:01:00Z");
        final var finished = Instant.parse("2026-09-30T10:05:00Z");
        final var scan = new Scan(
            Scan.ScanId.generate(), projectId, Scan.ScanStatus.COMPLETED,
            new Scan.SourceRevision("abc123", "main"), created, finished, started
        );

        when(scanService.getScans(any(), any(), eq(projectId)))
            .thenReturn(new Page<>(List.of(scan), 0, 20, 1, 1));

        final var body = mockMvc.perform(get("/api/v1/projects/{id}/scans", projectId.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].startedAt").isNotEmpty())
            .andExpect(jsonPath("$.content[0].completedAt").isNotEmpty())
            .andExpect(openApi().isValid(OPENAPI_SPEC))
            .andReturn()
            .getResponse()
            .getContentAsString();

        // A finished scan's last update is when it finished
        assertThat(JsonPath.<String>read(body, "$.content[0].completedAt"))
            .isEqualTo(JsonPath.<String>read(body, "$.content[0].updatedAt"));
    }

    private static Project project() {
        final var now = Instant.now();

        return new Project(
            Project.ProjectId.generate(), "backend", null, "https://example.com/shop.git", "backend",
            new System.SystemId(UUID.randomUUID()), now, now
        );
    }

    @Test
    void createScanBeyondThePlanLimit() throws Exception {
        when(scanService.create(any(), any(CreateScanCommand.class)))
            .thenThrow(new EntitlementException("The plan allows 100 scans"));

        mockMvc.perform(
            post("/api/v1/projects/{id}/scans", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branch\": \"main\", \"revision\": \"abc123\"}")
        )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PLAN_LIMIT"))
        .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void uploadScanResults() throws Exception {
        final var projectId = Project.ProjectId.generate();
        final var now = Instant.now();
        final var scan = new Scan(
            Scan.ScanId.generate(), projectId, Scan.ScanStatus.PENDING,
            new Scan.SourceRevision("abc123", "main"), now, now, null
        );

        when(scanService.upload(any(), any(UploadScanCommand.class))).thenReturn(scan);

        mockMvc.perform(
            multipart("/api/v1/projects/{id}/scan-results", projectId.id())
                .file(resultFile("backend.json", "backend"))
                .file(resultFile("frontend.json", "frontend"))
                .param("branch", "main")
                .param("revision", "abc123")
        )
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", endsWith("/api/v1/scans/" + scan.id().id() + "/steps")))
        .andExpect(openApi().isValid(RESPONSE_VALIDATOR));

        final var command = ArgumentCaptor.forClass(UploadScanCommand.class);
        verify(scanService).upload(any(), command.capture());

        assertThat(command.getValue().projectId()).isEqualTo(projectId);
        assertThat(command.getValue().branch()).isEqualTo("main");
        assertThat(command.getValue().revision()).isEqualTo("abc123");
        assertThat(command.getValue().results())
            .extracting(result -> result.getTarget().getPath())
            .containsExactly("backend", "frontend");
    }

    @Test
    void refusesAFileThatIsNotAScanResult() throws Exception {
        mockMvc.perform(
            multipart("/api/v1/projects/{id}/scan-results", UUID.randomUUID())
                .file(new MockMultipartFile("files", "broken.json", "application/json", "{\"modules\": [".getBytes()))
                .param("branch", "main")
                .param("revision", "abc123")
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").value(startsWith("File broken.json is not a valid scan result")))
        .andExpect(openApi().isValid(RESPONSE_VALIDATOR));

        verify(scanService, never()).upload(any(), any());
    }

    @Test
    void refusesAnUploadWithoutARevision() throws Exception {
        mockMvc.perform(
            multipart("/api/v1/projects/{id}/scan-results", UUID.randomUUID())
                .file(resultFile("backend.json", "backend"))
                .param("branch", "main")
        )
        .andExpect(status().isBadRequest());

        verify(scanService, never()).upload(any(), any());
    }

    private static MockMultipartFile resultFile(final String name, final String path) {
        final var json = """
            {
              "target": { "path": "%s", "language": "JAVA", "buildSystem": "MAVEN" },
              "modules": []
            }
            """.formatted(path);

        return new MockMultipartFile("files", name, "application/json", json.getBytes());
    }
}
