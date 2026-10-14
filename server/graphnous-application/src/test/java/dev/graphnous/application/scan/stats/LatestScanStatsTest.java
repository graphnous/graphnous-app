package dev.graphnous.application.scan.stats;

import dev.graphnous.application.scan.ScanRepository;
import dev.graphnous.application.project.ProjectRepository;
import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.stats.ScanStats;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LatestScanStatsTest {

    @Mock
    private ScanStatRepository scanStatRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private SystemService systemService;

    @Mock
    private AuthorizationService authorizationService;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final Project.ProjectId projectId = Project.ProjectId.generate();

    @Test
    void returnsTheStatsOfTheLatestScanWithAny() {
        final var stats = new ScanStats(ScanStats.ScanStatId.generate(), Scan.ScanId.generate(), projectId, 1, Map.of(), 2, 3);

        when(scanStatRepository.findLatestByProjectId(projectId)).thenReturn(Optional.of(stats));

        assertThat(service().getLatestStats(context, projectId)).contains(stats);

        verify(authorizationService).authorize(context, Permission.SCAN_READ);
        verify(projectService).getProject(context, projectId);
    }

    @Test
    void hasNoStatsWhenNoScanHasAny() {
        when(scanStatRepository.findLatestByProjectId(projectId)).thenReturn(Optional.empty());

        assertThat(service().getLatestStats(context, projectId)).isEmpty();

        // Still only for a project of the caller's
        verify(projectService).getProject(context, projectId);
    }

    @Test
    void addsUpTheLatestStatsOfTheProjectsOfASystem() {
        final var systemId = System.SystemId.generate();
        final var frontendId = Project.ProjectId.generate();

        when(scanStatRepository.findLatestBySystemId(systemId)).thenReturn(List.of(
            new ScanStats(
                ScanStats.ScanStatId.generate(), Scan.ScanId.generate(), projectId,
                2, Map.of("JAVA", List.of("src/Order.java")), 8, 31
            ),
            new ScanStats(
                ScanStats.ScanStatId.generate(), Scan.ScanId.generate(), frontendId,
                1, Map.of("JAVA", List.of("src/Cart.java"), "TYPESCRIPT", List.of("src/app.ts")), 3, 10
            )
        ));

        // Three projects, one not scanned yet, with seven scans between them
        when(projectRepository.count(systemId)).thenReturn(3);
        when(scanRepository.countInSystem(systemId)).thenReturn(7);

        final var stats = service().getSystemStats(context, systemId);

        assertThat(stats.systemId()).isEqualTo(systemId);
        assertThat(stats.numberOfProjectsScanned()).isEqualTo(2);
        assertThat(stats.numberOfProjects()).isEqualTo(3);
        assertThat(stats.numberOfScans()).isEqualTo(7);
        assertThat(stats.numberOfModulesScanned()).isEqualTo(3);
        assertThat(stats.languages()).containsExactly(
            Map.entry("JAVA", List.of("src/Order.java", "src/Cart.java")),
            Map.entry("TYPESCRIPT", List.of("src/app.ts"))
        );
        assertThat(stats.numberOfClassesParsed()).isEqualTo(11);
        assertThat(stats.numberOfMethodsParsed()).isEqualTo(41);

        verify(authorizationService).authorize(context, Permission.SCAN_READ);
        verify(systemService).getSystem(context, systemId);
    }

    @Test
    void countsNothingForASystemWithoutStats() {
        final var systemId = System.SystemId.generate();

        when(scanStatRepository.findLatestBySystemId(systemId)).thenReturn(List.of());

        final var stats = service().getSystemStats(context, systemId);

        assertThat(stats.numberOfProjectsScanned()).isZero();
        assertThat(stats.languages()).isEmpty();
    }

    @Test
    void countsTheScansOfAProject() {
        when(scanRepository.count(projectId)).thenReturn(4);

        assertThat(service().countScans(context, projectId)).isEqualTo(4);

        verify(authorizationService).authorize(context, Permission.SCAN_READ);
        verify(projectService).getProject(context, projectId);
    }

    private ScanStatService service() {
        return new ScanStatService(
            scanStatRepository, scanRepository, projectRepository, projectService, systemService, authorizationService
        );
    }
}
