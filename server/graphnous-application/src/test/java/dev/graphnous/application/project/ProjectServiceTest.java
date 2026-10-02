package dev.graphnous.application.project;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.entitlement.Entitlement;
import dev.graphnous.application.entitlement.EntitlementService;
import dev.graphnous.application.exception.EntitlementException;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private SystemService systemService;

    @Mock
    private ProjectDeleter projectDeleter;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final System.SystemId systemId = System.SystemId.generate();

    @Test
    void createsAProjectInASystemOfTheCaller() {
        when(projectRepository.count(systemId)).thenReturn(4);
        when(projectRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        final var project = service().create(
            context,
            new CreateProjectCommand("backend", "The API", "https://example.com/shop.git", "backend", systemId)
        );

        assertThat(project.name()).isEqualTo("backend");
        assertThat(project.gitUrl()).isEqualTo("https://example.com/shop.git");
        assertThat(project.path()).isEqualTo("backend");
        assertThat(project.systemId()).isEqualTo(systemId);

        verify(authorizationService).authorize(context, Permission.PROJECT_CREATE);
        verify(entitlementService).requireWithinLimit(context.organization(), Entitlement.PROJECTS, 4);
        verify(systemService).getSystem(context, systemId);
    }

    @Test
    void doesNotCreateAProjectInASystemOfAnotherOrganization() {
        when(systemService.getSystem(context, systemId)).thenThrow(new NotFoundException("Not found"));

        assertThrows(
            NotFoundException.class,
            () -> service().create(context, new CreateProjectCommand("backend", null, "https://example.com/shop.git", null, systemId))
        );

        verify(projectRepository, never()).save(any());
    }

    @Test
    void doesNotCreateAProjectBeyondTheLimit() {
        doThrow(new EntitlementException("Limit reached"))
            .when(entitlementService).requireWithinLimit(any(), eq(Entitlement.PROJECTS), any(Integer.class));

        assertThrows(
            EntitlementException.class,
            () -> service().create(context, new CreateProjectCommand("backend", null, "https://example.com/shop.git", null, systemId))
        );

        verify(projectRepository, never()).save(any());
    }

    @Test
    void readsAProjectThroughItsSystem() {
        final var project = project(Instant.now());

        when(projectRepository.findById(project.id())).thenReturn(project);

        assertThat(service().getProject(context, project.id())).isEqualTo(project);

        verify(authorizationService).authorize(context, Permission.PROJECT_READ);
        // The project's own system decides whether the caller may read it
        verify(systemService).getSystem(context, systemId);
    }

    @Test
    void doesNotReadAProjectOfAnotherOrganization() {
        final var project = project(Instant.now());

        when(projectRepository.findById(project.id())).thenReturn(project);
        when(systemService.getSystem(context, systemId)).thenThrow(new NotFoundException("Not found"));

        assertThrows(NotFoundException.class, () -> service().getProject(context, project.id()));
    }

    @Test
    void listsTheProjectsOfASystemOfTheCaller() {
        final var page = new Page<>(List.of(project(Instant.now())), 0, 20, 1, 1);

        when(projectRepository.findAll(eq(systemId), any())).thenReturn(page);

        assertThat(service().getProjects(context, PageQuery.of(0, 20), systemId)).isEqualTo(page);
        verify(systemService).getSystem(context, systemId);
    }

    @Test
    void doesNotListTheProjectsOfASystemOfAnotherOrganization() {
        when(systemService.getSystem(context, systemId)).thenThrow(new NotFoundException("Not found"));

        assertThrows(NotFoundException.class, () -> service().getProjects(context, PageQuery.of(0, 20), systemId));

        verify(projectRepository, never()).findAll(any(), any());
    }

    @Test
    void updatesAProjectKeepingItsSystemRepositoryAndCreationTime() {
        final var created = Instant.parse("2026-01-01T00:00:00Z");
        final var current = project(created);
        final var otherSystem = System.SystemId.generate();

        when(projectRepository.findById(current.id())).thenReturn(current);
        when(projectRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service().update(
            context,
            new UpdateProjectCommand(current.id(), "renamed", "New description", "api", otherSystem)
        );

        final var saved = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(saved.capture());

        assertThat(saved.getValue().name()).isEqualTo("renamed");
        assertThat(saved.getValue().path()).isEqualTo("api");
        assertThat(saved.getValue().systemId()).isEqualTo(systemId);
        assertThat(saved.getValue().gitUrl()).isEqualTo(current.gitUrl());
        assertThat(saved.getValue().createdAt()).isEqualTo(created);

        // Access is checked through the project's system, not the one asked for
        verify(systemService).getSystem(context, systemId);
        verify(systemService, never()).getSystem(context, otherSystem);
    }

    @Test
    void doesNotUpdateAProjectOfAnotherOrganization() {
        final var current = project(Instant.now());

        when(projectRepository.findById(current.id())).thenReturn(current);
        when(systemService.getSystem(context, systemId)).thenThrow(new NotFoundException("Not found"));

        assertThrows(
            NotFoundException.class,
            () -> service().update(context, new UpdateProjectCommand(current.id(), "renamed", null, null, System.SystemId.generate()))
        );

        verify(projectRepository, never()).save(any());
    }

    @Test
    void deletesAProjectThroughTheDeleter() {
        final var project = project(Instant.now());

        when(projectRepository.findById(project.id())).thenReturn(project);

        service().delete(context, new DeleteProjectCommand(project.id()));

        verify(authorizationService).authorize(context, Permission.PROJECT_DELETE);
        verify(projectDeleter).deleteProject(systemId, project.id());
    }

    @Test
    void deletesNothingOfAProjectOfAnotherOrganization() {
        final var project = project(Instant.now());

        when(projectRepository.findById(project.id())).thenReturn(project);
        when(systemService.getSystem(context, systemId)).thenThrow(new NotFoundException("Not found"));

        assertThrows(NotFoundException.class, () -> service().delete(context, new DeleteProjectCommand(project.id())));

        verify(projectDeleter, never()).deleteProject(any(), any());
    }

    private Project project(final Instant createdAt) {
        return new Project(
            Project.ProjectId.generate(),
            "backend",
            null,
            "https://example.com/shop.git",
            "backend",
            systemId,
            createdAt,
            createdAt
        );
    }

    private ProjectService service() {
        return new ProjectService(projectRepository, authorizationService, entitlementService, systemService, projectDeleter);
    }
}
