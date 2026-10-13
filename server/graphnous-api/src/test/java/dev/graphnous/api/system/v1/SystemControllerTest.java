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
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.dependencies.ScanDependency;
import dev.graphnous.application.scan.dependencies.ScanDependencyFilter;
import dev.graphnous.application.scan.dependencies.ScanDependencyService;
import dev.graphnous.application.scan.dependencies.SystemDependency;
import dev.graphnous.application.scan.dependencies.SystemLibrary;
import dev.graphnous.application.scan.links.HttpLinkService;
import dev.graphnous.application.scan.links.HttpLinks;
import dev.graphnous.application.scan.stats.ScanStatService;
import dev.graphnous.application.system.CreateSystemCommand;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.application.system.UpdateSystemCommand;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.stats.SystemStats;
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
import java.util.Map;
import java.util.UUID;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
            "https://graphnous.github.io/graphnous-schemas/openapi/v1.yaml";

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
    private ScanService scanService;

    @MockitoBean
    private ScanStatService scanStatService;

    @MockitoBean
    private ScanDependencyService scanDependencyService;

    @MockitoBean
    private HttpLinkService httpLinkService;

    @MockitoBean
    private RequestContextProvider contextProvider;

    // Not in the published OpenAPI spec yet, so not validated against it

    @Test
    void getStats() throws Exception {
        final var systemId = new System.SystemId(UUID.randomUUID());

        when(scanStatService.getSystemStats(any(), eq(systemId))).thenReturn(
            new SystemStats(
                systemId,
                2,
                3,
                Map.of("JAVA", List.of("backend/src/main/java/com/example/Order.java")),
                11,
                41
            )
        );

        mockMvc.perform(get("/api/v1/systems/{id}/stats", systemId.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.systemId").value(systemId.id().toString()))
            .andExpect(jsonPath("$.numberOfProjectsScanned").value(2))
            .andExpect(jsonPath("$.numberOfModulesScanned").value(3))
            .andExpect(jsonPath("$.languages.JAVA[0]").value("backend/src/main/java/com/example/Order.java"))
            .andExpect(jsonPath("$.numberOfClassesParsed").value(11))
            .andExpect(jsonPath("$.numberOfMethodsParsed").value(41));
    }

    @Test
    void getStatsOfAnUnknownSystem() throws Exception {
        when(scanStatService.getSystemStats(any(), any()))
            .thenThrow(new NotFoundException("System not found"));

        mockMvc.perform(get("/api/v1/systems/{id}/stats", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void getDependencies() throws Exception {
        final var systemId = new System.SystemId(UUID.randomUUID());
        final var projectId = Project.ProjectId.generate();
        final var scanId = Scan.ScanId.generate();

        when(scanDependencyService.getSystemDependencies(
            any(),
            eq(systemId),
            eq(new ScanDependencyFilter("junit", null, "test")),
            eq(new PageQuery(0, 50, new dev.graphnous.application.pagination.Sort(
                "name", dev.graphnous.application.pagination.Sort.Direction.ASC
            )))
        )).thenReturn(new Page<>(
            List.of(new SystemDependency(
                projectId,
                scanId,
                new ScanDependency("dependency:org.junit.jupiter:junit-jupiter:", "orders", "org.junit.jupiter:junit-jupiter", null, "test")
            )),
            0, 50, 1, 1
        ));

        mockMvc.perform(
            get("/api/v1/systems/{id}/dependencies", systemId.id())
                .queryParam("query", "junit")
                .queryParam("scope", "test")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value("dependency:org.junit.jupiter:junit-jupiter:"))
            .andExpect(jsonPath("$.content[0].projectId").value(projectId.id().toString()))
            .andExpect(jsonPath("$.content[0].scanId").value(scanId.id().toString()))
            .andExpect(jsonPath("$.content[0].module").value("orders"))
            .andExpect(jsonPath("$.content[0].name").value("org.junit.jupiter:junit-jupiter"))
            .andExpect(jsonPath("$.content[0].scope").value("test"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getLinks() throws Exception {
        final var systemId = new System.SystemId(UUID.randomUUID());
        final var frontendScan = Scan.ScanId.generate();
        final var backendScan = Scan.ScanId.generate();
        final var frontend = Project.ProjectId.generate();
        final var backend = Project.ProjectId.generate();

        when(httpLinkService.getLinks(any(), eq(systemId), eq(List.of(frontendScan, backendScan)))).thenReturn(
            new HttpLinks.Found(
                List.of(new HttpLinks.Link(
                    new HttpLinks.Call("call", frontendScan, frontend, null, "/api/orders/{id}", "fetch"),
                    new HttpLinks.Endpoint("endpoint", backendScan, backend, "GET", "/api/orders/{id}"),
                    HttpLinks.Match.PATH
                )),
                4,
                9
            )
        );

        mockMvc.perform(
            get("/api/v1/systems/{id}/links", systemId.id())
                .queryParam("scans", frontendScan.id() + "," + backendScan.id())
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.links[0].call.id").value("call"))
            .andExpect(jsonPath("$.links[0].call.projectId").value(frontend.id().toString()))
            .andExpect(jsonPath("$.links[0].call.httpMethod").isEmpty())
            .andExpect(jsonPath("$.links[0].endpoint.scanId").value(backendScan.id().toString()))
            .andExpect(jsonPath("$.links[0].endpoint.httpMethod").value("GET"))
            .andExpect(jsonPath("$.links[0].match").value("PATH"))
            .andExpect(jsonPath("$.links[0].crossesProjects").value(true))
            .andExpect(jsonPath("$.calls").value(4))
            .andExpect(jsonPath("$.endpoints").value(9));
    }

    @Test
    void getLinksOfTheLatestScansByDefault() throws Exception {
        final var systemId = new System.SystemId(UUID.randomUUID());

        when(httpLinkService.getLinks(any(), eq(systemId), isNull())).thenReturn(new HttpLinks.Found(List.of(), 0, 0));

        mockMvc.perform(get("/api/v1/systems/{id}/links", systemId.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.links").isEmpty());
    }

    @Test
    void getLibraries() throws Exception {
        final var systemId = new System.SystemId(UUID.randomUUID());
        final var backend = Project.ProjectId.generate();
        final var frontend = Project.ProjectId.generate();

        when(scanDependencyService.getSystemLibraries(
            any(),
            eq(systemId),
            eq("slf4j"),
            eq(true),
            eq(new PageQuery(0, 50, new dev.graphnous.application.pagination.Sort(
                "versions", dev.graphnous.application.pagination.Sort.Direction.DESC
            )))
        )).thenReturn(new Page<>(
            List.of(new SystemLibrary("org.slf4j:slf4j-api", List.of(
                new SystemLibrary.Version("2.0.18", List.of(backend)),
                new SystemLibrary.Version("2.0.9", List.of(frontend))
            ))),
            0, 50, 1, 1
        ));

        mockMvc.perform(
            get("/api/v1/systems/{id}/libraries", systemId.id())
                .queryParam("query", "slf4j")
                .queryParam("drift", "true")
                .queryParam("sort", "versions")
                .queryParam("direction", "desc")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value("org.slf4j:slf4j-api"))
            .andExpect(jsonPath("$.content[0].versions[0].version").value("2.0.18"))
            .andExpect(jsonPath("$.content[0].versions[0].projects[0]").value(backend.id().toString()))
            .andExpect(jsonPath("$.content[0].versions[1].projects[0]").value(frontend.id().toString()))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getLibrariesWithAnInvalidSort() throws Exception {
        mockMvc.perform(
            get("/api/v1/systems/{id}/libraries", UUID.randomUUID())
                .param("sort", "scope")
        )
        .andExpect(status().isBadRequest());
    }

    @Test
    void getDependenciesOfAnUnknownSystem() throws Exception {
        when(scanDependencyService.getSystemDependencies(any(), any(), any(), any()))
            .thenThrow(new NotFoundException("System not found"));

        mockMvc.perform(get("/api/v1/systems/{id}/dependencies", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void getDependenciesWithAnInvalidSort() throws Exception {
        mockMvc.perform(
            get("/api/v1/systems/{id}/dependencies", UUID.randomUUID())
                .param("sort", "version")
        )
        .andExpect(status().isBadRequest());
    }

    @Test
    void getScansNewestFirstByDefault() throws Exception {
        final var systemId = new System.SystemId(UUID.randomUUID());
        final var now = Instant.now();
        final var scan = new Scan(
            Scan.ScanId.generate(), Project.ProjectId.generate(), Scan.ScanStatus.COMPLETED,
            new Scan.SourceRevision("abc123", "main"), now, now, now
        );

        when(scanService.getSystemScans(
            any(),
            argThat((PageQuery query) ->
                query.page() == 0 && query.size() == 20
                    && query.sort().property().equals("createdAt")
                    && query.sort().direction() == dev.graphnous.application.pagination.Sort.Direction.DESC
            ),
            eq(systemId)
        )).thenReturn(new Page<>(List.of(scan), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/systems/{id}/scans", systemId.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(scan.id().id().toString()))
            .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getScansOfAnUnknownSystem() throws Exception {
        when(scanService.getSystemScans(any(), any(), any()))
            .thenThrow(new NotFoundException("System not found"));

        mockMvc.perform(get("/api/v1/systems/{id}/scans", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void getScansWithAnInvalidSort() throws Exception {
        mockMvc.perform(
            get("/api/v1/systems/{id}/scans", UUID.randomUUID())
                .param("sort", "branch")
        )
        .andExpect(status().isBadRequest());
    }

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
