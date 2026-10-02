package dev.graphnous.api.system.v1;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.exception.ConflictException;
import dev.graphnous.application.exception.EntitlementException;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.project.CreateProjectCommand;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.system.CreateSystemCommand;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.application.system.UpdateSystemCommand;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.system.System;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SystemControllerTest {

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
    private SystemService systemService;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private RequestContextProvider contextProvider;

    @Test
    void getSystems() throws Exception {
        final Page<System> page = new Page<>(
            List.of(),
            20,
            1,
            1,
            1
        );

        when(systemService.getSystems(
            any(),
            any(PageQuery.class)
        )).thenReturn(page);

        mockMvc.perform(
            get("/api/v1/systems")
                .param("page", "0")
                .param("size", "20")
                .param("sort", "name")
                .param("direction", "asc")
        )
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void createSystemWithAnInvalidRequest() throws Exception {
        mockMvc.perform(
            post("/api/v1/systems")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\"}")
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(openApi().isValid(RESPONSE_VALIDATOR));
    }

    @Test
    void createSystemWithMalformedJson() throws Exception {
        mockMvc.perform(
            post("/api/v1/systems")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": ")
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
        .andExpect(openApi().isValid(RESPONSE_VALIDATOR));
    }

    @Test
    void getSystemWithAnInvalidId() throws Exception {
        mockMvc.perform(get("/api/v1/systems/not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unknownPathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/unknown"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unsupportedMethodIsNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/v1/systems"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void getSystemsSortedByCreationDescending() throws Exception {
        when(systemService.getSystems(any(), any(PageQuery.class)))
            .thenReturn(new Page<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(
            get("/api/v1/systems")
                .param("sort", "createdAt")
                .param("direction", "desc")
        )
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void getSystemsWithInvalidPaging() throws Exception {
        for (final var param : List.of(
            new String[] {"size", "101"},
            new String[] {"size", "0"},
            new String[] {"page", "-1"},
            new String[] {"sort", "password"},
            new String[] {"direction", "up"}
        )) {
            mockMvc.perform(get("/api/v1/systems").param(param[0], param[1]))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(RESPONSE_VALIDATOR));
        }
    }

    @Test
    void getProjectsOfASystem() throws Exception {
        when(projectService.getProjects(any(), any(PageQuery.class), any()))
            .thenReturn(new Page<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/systems/{id}/projects", UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void getProjectsOfASystemWithInvalidPaging() throws Exception {
        mockMvc.perform(
            get("/api/v1/systems/{id}/projects", UUID.randomUUID())
                .param("size", "500")
        )
        .andExpect(status().isBadRequest())
        .andExpect(openApi().isValid(RESPONSE_VALIDATOR));
    }

    @Test
    void deleteSystemWithActiveScans() throws Exception {
        doThrow(new ConflictException("A project has active scans"))
            .when(systemService).delete(any(), any());

        mockMvc.perform(delete("/api/v1/systems/{id}", UUID.randomUUID()))
            .andExpect(status().isConflict())
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void getSystem() throws Exception {
        final var system = system();

        when(systemService.getSystem(any(), eq(system.id()))).thenReturn(system);

        mockMvc.perform(get("/api/v1/systems/{id}", system.id().id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Shop"))
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void getUnknownSystem() throws Exception {
        when(systemService.getSystem(any(), any())).thenThrow(new NotFoundException("System not found"));

        mockMvc.perform(get("/api/v1/systems/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void createSystem() throws Exception {
        final var system = system();

        when(systemService.create(any(), any(CreateSystemCommand.class))).thenReturn(system);

        mockMvc.perform(
            post("/api/v1/systems")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Shop\", \"description\": \"The web shop\"}")
        )
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "http://localhost/api/v1/systems/" + system.id().id()))
        .andExpect(openApi().isValid(OPENAPI_SPEC));

        verify(systemService).create(any(), eq(new CreateSystemCommand("Shop", "The web shop")));
    }

    @Test
    void updateSystem() throws Exception {
        final var system = system();

        when(systemService.update(any(), any(UpdateSystemCommand.class))).thenReturn(system);

        mockMvc.perform(
            put("/api/v1/systems/{id}", system.id().id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Shop\"}")
        )
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(OPENAPI_SPEC));

        verify(systemService).update(any(), eq(new UpdateSystemCommand(system.id(), "Shop", null)));
    }

    @Test
    void deleteSystem() throws Exception {
        final var id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/systems/{id}", id))
            .andExpect(status().isNoContent())
            .andExpect(openApi().isValid(OPENAPI_SPEC));

        verify(systemService).delete(any(), eq(new System.SystemId(id)));
    }

    @Test
    void createProjectInASystem() throws Exception {
        final var systemId = UUID.randomUUID();
        final var now = Instant.now();
        final var project = new Project(
            Project.ProjectId.generate(), "backend", null, "https://example.com/shop.git", "backend",
            new System.SystemId(systemId), now, now
        );

        when(projectService.create(any(), any(CreateProjectCommand.class))).thenReturn(project);

        mockMvc.perform(
            post("/api/v1/systems/{id}/projects", systemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"backend\", \"gitUrl\": \"https://example.com/shop.git\", \"path\": \"backend\"}")
        )
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "http://localhost/api/v1/projects/" + project.id().id()))
        .andExpect(openApi().isValid(OPENAPI_SPEC));

        verify(projectService).create(any(), eq(new CreateProjectCommand(
            "backend", null, "https://example.com/shop.git", "backend", new System.SystemId(systemId)
        )));
    }

    private static System system() {
        final var now = Instant.now();

        return new System(System.SystemId.generate(), "Shop", "The web shop", now, now);
    }

    @Test
    void createSystemBeyondThePlanLimit() throws Exception {
        when(systemService.create(any(), any(CreateSystemCommand.class)))
            .thenThrow(new EntitlementException("The plan allows 3 systems"));

        mockMvc.perform(
            post("/api/v1/systems")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Shop\"}")
        )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PLAN_LIMIT"))
        .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void createProjectBeyondThePlanLimit() throws Exception {
        when(projectService.create(any(), any(CreateProjectCommand.class)))
            .thenThrow(new EntitlementException("The plan allows 10 projects"));

        mockMvc.perform(
            post("/api/v1/systems/{id}/projects", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"backend\", \"gitUrl\": \"https://example.com/shop.git\"}")
        )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PLAN_LIMIT"))
        .andExpect(openApi().isValid(OPENAPI_SPEC));
    }
}
