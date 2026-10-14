package dev.graphnous.application.scan.links;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpLinkServiceTest {

    @Mock
    private ScanService scanService;

    @Mock
    private HttpLinkRepository repository;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final System.SystemId systemId = System.SystemId.generate();

    private final Scan frontend = scan();
    private final Scan backend = scan();

    @Test
    void linksTheLatestCompletedScansOfTheSystemByDefault() {
        final var scans = List.of(frontend, backend);
        final var call = new HttpLinks.Call("call", frontend.id(), frontend.projectId(), "GET", "/api/orders", "fetch");
        final var endpoint = new HttpLinks.Endpoint("endpoint", backend.id(), backend.projectId(), "GET", "/api/orders");
        final var unused = new HttpLinks.Endpoint("unused", backend.id(), backend.projectId(), "GET", "/api/customers");

        when(scanService.getLatestCompletedScans(context, systemId)).thenReturn(scans);
        when(repository.findCalls(scans)).thenReturn(List.of(call));
        when(repository.findEndpoints(scans)).thenReturn(List.of(endpoint, unused));

        final var found = new HttpLinkService(scanService, repository).getLinks(context, systemId, null);

        assertThat(found.links()).containsExactly(new HttpLinks.Link(call, endpoint, HttpLinks.Match.PATH));
        assertThat(found.calls()).isEqualTo(1);
        assertThat(found.endpoints()).isEqualTo(2);
        verify(scanService, never()).getScan(any(), any());
    }

    @Test
    void linksTheScansAskedForWhenTheUserMayReadThem() {
        when(scanService.getScan(context, frontend.id())).thenReturn(frontend);
        when(scanService.getScan(context, backend.id())).thenReturn(backend);
        when(repository.findCalls(List.of(frontend, backend))).thenReturn(List.of());
        when(repository.findEndpoints(List.of(frontend, backend))).thenReturn(List.of());

        // Each scan once
        final var found = new HttpLinkService(scanService, repository)
            .getLinks(context, systemId, List.of(frontend.id(), backend.id(), frontend.id()));

        assertThat(found.links()).isEmpty();
        verify(scanService, never()).getLatestCompletedScans(any(), any());
    }

    @Test
    void linksNoScanTheUserMayNotRead() {
        when(scanService.getScan(context, frontend.id())).thenThrow(new NotFoundException("Scan not found"));

        assertThatThrownBy(() -> new HttpLinkService(scanService, repository).getLinks(context, systemId, List.of(frontend.id())))
            .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void refusesTooManyScans() {
        final var scanIds = IntStream.rangeClosed(0, HttpLinkService.MAX_SCANS).mapToObj(i -> Scan.ScanId.generate()).toList();

        assertThatThrownBy(() -> new HttpLinkService(scanService, repository).getLinks(context, systemId, scanIds))
            .isInstanceOf(ValidationException.class);

        verifyNoInteractions(scanService, repository);
    }

    private static Scan scan() {
        final var now = Instant.now();

        return new Scan(
            Scan.ScanId.generate(), Project.ProjectId.generate(), Scan.ScanStatus.COMPLETED,
            new Scan.SourceRevision("abc123", "main"), now, now, now
        );
    }
}
