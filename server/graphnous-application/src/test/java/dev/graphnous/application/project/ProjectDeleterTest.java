package dev.graphnous.application.project;

import dev.graphnous.application.exception.ConflictException;
import dev.graphnous.application.scan.ScanDeleter;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectDeleterTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ScanDeleter scanDeleter;

    private final System.SystemId systemId = new System.SystemId(UUID.randomUUID());

    @Test
    void deletesTheScansBeforeTheProject() {
        final var projectId = Project.ProjectId.generate();

        deleter().deleteProject(systemId, projectId);

        final InOrder order = inOrder(scanDeleter, projectRepository);
        order.verify(scanDeleter).deleteScans(projectId);
        order.verify(projectRepository).delete(systemId, projectId);
    }

    @Test
    void deletesEveryProjectOfTheSystem() {
        final var first = Project.ProjectId.generate();
        final var second = Project.ProjectId.generate();

        when(projectRepository.findIdsBySystemId(systemId)).thenReturn(List.of(first, second));

        deleter().deleteProjects(systemId);

        final InOrder order = inOrder(scanDeleter, projectRepository);

        for (final var projectId : List.of(first, second)) {
            order.verify(scanDeleter).deleteScans(projectId);
            order.verify(projectRepository).delete(systemId, projectId);
        }
    }

    @Test
    void refusesToDeleteAProjectWithActiveScans() {
        final var projectId = Project.ProjectId.generate();

        doThrow(new ConflictException("Active scans"))
            .when(scanDeleter).requireNoActiveScans(projectId);

        assertThrows(ConflictException.class, () -> deleter().deleteProject(systemId, projectId));

        verify(scanDeleter, never()).deleteScans(any());
        verify(projectRepository, never()).delete(any(), any());
    }

    @Test
    void deletesNoProjectOfASystemWithAnActiveScan() {
        final var idle = Project.ProjectId.generate();
        final var busy = Project.ProjectId.generate();

        when(projectRepository.findIdsBySystemId(systemId)).thenReturn(List.of(idle, busy));
        doNothing().when(scanDeleter).requireNoActiveScans(idle);
        doThrow(new ConflictException("Active scans"))
            .when(scanDeleter).requireNoActiveScans(busy);

        assertThrows(ConflictException.class, () -> deleter().deleteProjects(systemId));

        // Not even the project before the busy one
        verify(scanDeleter, never()).deleteScans(any());
        verify(projectRepository, never()).delete(any(), any());
    }

    private ProjectDeleter deleter() {
        return new ProjectDeleter(projectRepository, scanDeleter);
    }
}
