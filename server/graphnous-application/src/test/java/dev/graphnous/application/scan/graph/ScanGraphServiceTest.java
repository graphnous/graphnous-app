package dev.graphnous.application.scan.graph;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.AuthorizationException;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanGraphServiceTest {

    @Mock
    private ScanService scanService;

    @Mock
    private ScanGraphRepository scanGraphRepository;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final Scan.ScanId scanId = Scan.ScanId.generate();

    @Test
    void readsTheGraphOfAScanTheUserMayRead() {
        final var overview = new ScanGraph.Overview(List.of());

        when(scanGraphRepository.getOverview(scanId)).thenReturn(overview);

        assertThat(service().getOverview(context, scanId)).isSameAs(overview);

        verify(scanService).getScan(context, scanId);
    }

    @Test
    void doesNotReadTheGraphOfAScanTheUserMayNotRead() {
        when(scanService.getScan(context, scanId)).thenThrow(new AuthorizationException("Not allowed"));

        assertThatThrownBy(() -> service().findClasses(context, scanId, "Order", 10))
            .isInstanceOf(AuthorizationException.class);

        verifyNoInteractions(scanGraphRepository);
    }

    @Test
    void keepsSearchesWithinTheLimit() {
        service().findClasses(context, scanId, " Order ", 10_000);
        service().findAnnotated(context, scanId, "Entity", 0);

        verify(scanGraphRepository).findClasses(scanId, "Order", ScanGraphService.MAX_LIMIT);
        verify(scanGraphRepository).findAnnotated(scanId, "Entity", 1);
    }

    @Test
    void findsAnnotationsAsTheyAreWrittenInCode() {
        service().findAnnotated(context, scanId, "@GetMapping", 50);

        verify(scanGraphRepository).findAnnotated(scanId, "GetMapping", 50);
    }

    @Test
    void requiresTheAnnotation() {
        assertThatThrownBy(() -> service().findAnnotated(context, scanId, " ", 50))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void failsForAClassThatIsNotInTheScan() {
        when(scanGraphRepository.findClass(any(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getClass(context, scanId, "com.example.Missing"))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("com.example.Missing");
    }

    @Test
    void showsTheTargetsAndModulesOfTheScanByDefault() {
        when(scanGraphRepository.findNeighbourhood(any(), any(), anyInt(), anyInt()))
            .thenReturn(Optional.of(new ScanGraph.Neighbourhood("scan", 2, List.of(), List.of(), false)));

        service().getNeighbourhood(context, scanId, null, null);
        service().getNeighbourhood(context, scanId, " ", null);

        verify(scanGraphRepository, times(2))
            .findNeighbourhood(scanId, null, ScanGraphService.DEFAULT_SCAN_DEPTH, ScanGraphService.MAX_NODES);
        verify(scanService, times(2)).getScan(context, scanId);
    }

    @Test
    void showsTheNeighboursOfAFocusedNodeByDefault() {
        when(scanGraphRepository.findNeighbourhood(any(), any(), anyInt(), anyInt()))
            .thenReturn(Optional.of(new ScanGraph.Neighbourhood("class", 1, List.of(), List.of(), false)));

        service().getNeighbourhood(context, scanId, " class ", null);

        verify(scanGraphRepository)
            .findNeighbourhood(scanId, "class", ScanGraphService.DEFAULT_FOCUS_DEPTH, ScanGraphService.MAX_NODES);
    }

    @Test
    void refusesADepthOutOfRange() {
        assertThatThrownBy(() -> service().getNeighbourhood(context, scanId, null, ScanGraphService.MAX_DEPTH + 1))
            .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service().getNeighbourhood(context, scanId, null, -1))
            .isInstanceOf(ValidationException.class);

        verifyNoInteractions(scanGraphRepository);
    }

    @Test
    void failsForAFocusThatIsNotInTheScan() {
        when(scanGraphRepository.findNeighbourhood(any(), any(), anyInt(), anyInt())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getNeighbourhood(context, scanId, "missing", 1))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("missing");
    }

    private ScanGraphService service() {
        return new ScanGraphService(scanService, scanGraphRepository);
    }
}
