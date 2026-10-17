package dev.graphnous.application.scan.links;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.project.ProjectRepository;
import dev.graphnous.application.scan.ScanRepository;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private ProjectRepository projectRepository;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final System.SystemId systemId = System.SystemId.generate();

    private final Scan frontend = scan();
    private final Scan backend = scan();

    private final HttpLinks.Call call =
        new HttpLinks.Call("call", frontend.id(), frontend.projectId(), "GET", "/api/orders", "fetch");
    private final HttpLinks.Endpoint endpoint =
        new HttpLinks.Endpoint("endpoint", backend.id(), backend.projectId(), "GET", "/api/orders");
    private final HttpLinks.Endpoint unused =
        new HttpLinks.Endpoint("unused", backend.id(), backend.projectId(), "GET", "/api/customers");
    private final HttpLinks.Link link = new HttpLinks.Link(call, endpoint, HttpLinks.Match.PATH);

    @Test
    void readsTheStoredLinksOfTheLatestCompletedScansOfTheSystemByDefault() {
        final var scans = List.of(frontend, backend);

        when(scanService.getLatestCompletedScans(context, systemId)).thenReturn(scans);
        when(repository.findCalls(scans)).thenReturn(List.of(call));
        when(repository.findEndpoints(scans)).thenReturn(List.of(endpoint, unused));
        when(repository.findLinkedWith(scans)).thenReturn(Map.of(
            frontend.id(), HttpLinkMatcher.VERSION,
            backend.id(), HttpLinkMatcher.VERSION
        ));
        when(repository.findStoredLinks(scans)).thenReturn(List.of(link));

        final var found = service().getLinks(context, systemId, null);

        assertThat(found.links()).containsExactly(link);
        assertThat(found.calls()).isEqualTo(1);
        assertThat(found.endpoints()).isEqualTo(2);
        verify(repository, never()).replaceLinks(any(), any(), any());
        verify(scanService, never()).getScan(any(), any());
    }

    @Test
    void storesTheLinksOfTheLatestScansThatHaveNoneOfThisMatchingFirst() {
        final var scans = List.of(frontend, backend);

        when(scanService.getLatestCompletedScans(context, systemId)).thenReturn(scans);
        when(repository.findCalls(scans)).thenReturn(List.of(call));
        when(repository.findEndpoints(scans)).thenReturn(List.of(endpoint, unused));
        // The backend's are of an earlier version of the matching
        when(repository.findLinkedWith(scans)).thenReturn(Map.of(frontend.id(), HttpLinkMatcher.VERSION, backend.id(), "1"));
        when(repository.findStoredLinks(scans)).thenReturn(List.of(link));

        service().getLinks(context, systemId, null);

        verify(repository).replaceLinks(backend.id(), List.of(link), HttpLinkMatcher.VERSION);
        verify(repository, never()).replaceLinks(eq(frontend.id()), any(), any());
    }

    @Test
    void readsTheStoredLinksWhenTheScansAskedForAreTheLatest() {
        final var scans = List.of(frontend, backend);

        when(scanService.getScan(context, frontend.id())).thenReturn(frontend);
        when(scanService.getScan(context, backend.id())).thenReturn(backend);
        when(scanService.getLatestCompletedScans(context, systemId)).thenReturn(List.of(backend, frontend));
        when(repository.findCalls(scans)).thenReturn(List.of(call));
        when(repository.findEndpoints(scans)).thenReturn(List.of(endpoint));
        when(repository.findLinkedWith(scans)).thenReturn(Map.of());
        when(repository.findStoredLinks(scans)).thenReturn(List.of(link));

        // As a graph of the system shows them by default
        final var found = service().getLinks(context, systemId, List.of(frontend.id(), backend.id()));

        assertThat(found.links()).containsExactly(link);
        verify(repository).replaceLinks(eq(frontend.id()), any(), eq(HttpLinkMatcher.VERSION));
        verify(repository).replaceLinks(eq(backend.id()), any(), eq(HttpLinkMatcher.VERSION));
    }

    @Test
    void storesTheLinksOfACompletedScanAgainstTheLatestScansOfTheOtherProjects() {
        final var older = scan(frontend.projectId());
        final var latestFrontend = scan(frontend.projectId());
        final var project = new Project(
            frontend.projectId(), "frontend", null, "https://example.com/frontend.git", null,
            systemId, Instant.now(), Instant.now()
        );
        final var scans = List.of(older, backend);
        final var oldCall = new HttpLinks.Call("old", older.id(), older.projectId(), "GET", "/api/orders", "fetch");
        final var other = new HttpLinks.Call("other", backend.id(), backend.projectId(), "GET", "/api/customers", "fetch");

        when(scanRepository.findById(older.id())).thenReturn(older);
        when(projectRepository.findById(frontend.projectId())).thenReturn(project);
        when(scanRepository.findLatestCompletedInSystem(systemId)).thenReturn(List.of(latestFrontend, backend));
        when(repository.findCalls(scans)).thenReturn(List.of(oldCall, other));
        when(repository.findEndpoints(scans)).thenReturn(List.of(endpoint, unused));

        // As when an older scan of the frontend is enhanced again
        service().refresh(older.id());

        // Not the backend's own call, which this scan has no part in
        verify(repository).replaceLinks(
            older.id(),
            List.of(new HttpLinks.Link(oldCall, endpoint, HttpLinks.Match.PATH)),
            HttpLinkMatcher.VERSION
        );
    }

    @Test
    void storesNoLinksOfAScanThatIsNotCompletedOrGone() {
        final var running = new Scan(
            Scan.ScanId.generate(), frontend.projectId(), Scan.ScanStatus.RUNNING,
            new Scan.SourceRevision("abc123", "main"), Instant.now(), Instant.now(), Instant.now()
        );
        final var gone = Scan.ScanId.generate();

        when(scanRepository.findById(running.id())).thenReturn(running);
        when(scanRepository.findById(gone)).thenThrow(new NotFoundException("Scan not found"));

        service().refresh(running.id());
        service().refresh(gone);

        verifyNoInteractions(repository);
    }

    @Test
    void linksTheScansAskedForWhenTheUserMayReadThem() {
        when(scanService.getScan(context, frontend.id())).thenReturn(frontend);
        when(scanService.getScan(context, backend.id())).thenReturn(backend);
        when(repository.findCalls(List.of(frontend, backend))).thenReturn(List.of());
        when(repository.findEndpoints(List.of(frontend, backend))).thenReturn(List.of());

        // Not the latest: found anew, not stored
        when(scanService.getLatestCompletedScans(context, systemId)).thenReturn(List.of(scan(), backend));

        // Each scan once
        final var found = service()
            .getLinks(context, systemId, List.of(frontend.id(), backend.id(), frontend.id()));

        assertThat(found.links()).isEmpty();
        verify(repository, never()).findStoredLinks(any());
    }

    @Test
    void linksNoScanTheUserMayNotRead() {
        when(scanService.getScan(context, frontend.id())).thenThrow(new NotFoundException("Scan not found"));

        assertThatThrownBy(() -> service().getLinks(context, systemId, List.of(frontend.id())))
            .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void refusesTooManyScans() {
        final var scanIds = IntStream.rangeClosed(0, HttpLinkService.MAX_SCANS).mapToObj(i -> Scan.ScanId.generate()).toList();

        assertThatThrownBy(() -> service().getLinks(context, systemId, scanIds))
            .isInstanceOf(ValidationException.class);

        verifyNoInteractions(scanService, repository);
    }

    @Test
    void listsTheEndpointsOfTheSystemWithWhatServesAndCallsThem() {
        final var scans = latestScans();
        final var handler = new HttpLinks.Source("m1", "Method", "list", "com.acme.Orders.list()", "com.acme.Orders", "Orders.java", null);
        final var caller = new HttpLinks.Source("f1", "Method", "loadOrders", "loadOrders", null, "src/orders.ts", 12);

        when(repository.findSources(eq(scans), any())).thenReturn(Map.of("endpoint", handler, "call", caller));

        final var found = service().getEndpoints(context, systemId, HttpLinks.EndpointFilter.none(), 50);

        assertThat(found.total()).isEqualTo(2);
        assertThat(found.truncated()).isFalse();
        assertThat(found.endpoints()).containsExactly(
            new HttpLinks.EndpointCalls(unused, null, List.of()),
            new HttpLinks.EndpointCalls(endpoint, handler, List.of(new HttpLinks.CallSite(call, caller, HttpLinks.Match.PATH)))
        );
        verify(repository).findSources(scans, Set.of("endpoint", "unused", "call"));
    }

    @Test
    void listsOnlyTheEndpointsNoCallRequests() {
        latestScans();
        when(repository.findSources(any(), any())).thenReturn(Map.of());

        assertThat(service().getEndpoints(context, systemId, new HttpLinks.EndpointFilter(null, null, true), 50).endpoints())
            .extracting(HttpLinks.EndpointCalls::endpoint)
            .containsExactly(unused);
    }

    @Test
    void filtersTheEndpointsByUrlAndProject() {
        latestScans();
        when(repository.findSources(any(), any())).thenReturn(Map.of());

        assertThat(service().getEndpoints(context, systemId, new HttpLinks.EndpointFilter(" ORDERS ", null, false), 50).endpoints())
            .extracting(HttpLinks.EndpointCalls::endpoint)
            .containsExactly(endpoint);
        assertThat(service().getEndpoints(context, systemId, new HttpLinks.EndpointFilter(null, frontend.projectId(), false), 50).total())
            .isZero();
    }

    @Test
    void listsAtMostTheLimit() {
        latestScans();
        when(repository.findSources(any(), any())).thenReturn(Map.of());

        final var found = service().getEndpoints(context, systemId, HttpLinks.EndpointFilter.none(), 1);

        assertThat(found.endpoints()).extracting(HttpLinks.EndpointCalls::endpoint).containsExactly(unused);
        assertThat(found.total()).isEqualTo(2);
        assertThat(found.truncated()).isTrue();
    }

    @Test
    void listsTheCallsOfNoEndpointWithWhereTheyAre() {
        final var scans = latestScans();
        final var external = new HttpLinks.Call("external", frontend.id(), frontend.projectId(), "GET", "https://api.stripe.com/v1/charges", "fetch");
        final var unknown = new HttpLinks.Call("unknown", frontend.id(), frontend.projectId(), "GET", null, "fetch");
        final var wrapper = new HttpLinks.Call("wrapper", frontend.id(), frontend.projectId(), null, "{baseUrl}/{path}", "fetch");
        final var caller = new HttpLinks.Source("f1", "Method", "charge", "charge", null, "src/pay.ts", 4);

        when(repository.findCalls(scans)).thenReturn(List.of(call, external, unknown, wrapper));
        when(repository.findSources(scans, List.of("external"))).thenReturn(Map.of("external", caller));

        final var found = service().getUnmatchedCalls(context, systemId, HttpLinks.CallFilter.none(), 50);

        // Not the call of the orders, which has an endpoint, nor those of no known url
        assertThat(found.calls()).containsExactly(new HttpLinks.CallSite(external, caller, null));
        assertThat(found.total()).isEqualTo(1);
        assertThat(found.truncated()).isFalse();
    }

    @Test
    void filtersTheCallsOfNoEndpointAndListsAtMostTheLimit() {
        final var scans = latestScans();
        final var first = new HttpLinks.Call("a", frontend.id(), frontend.projectId(), "GET", "/api/invoices", "fetch");
        final var second = new HttpLinks.Call("b", frontend.id(), frontend.projectId(), "GET", "/api/payments", "fetch");

        when(repository.findCalls(scans)).thenReturn(List.of(call, second, first));
        when(repository.findSources(any(), any())).thenReturn(Map.of());

        final var limited = service().getUnmatchedCalls(context, systemId, HttpLinks.CallFilter.none(), 1);

        assertThat(limited.calls()).extracting(site -> site.call().id()).containsExactly("a");
        assertThat(limited.total()).isEqualTo(2);
        assertThat(limited.truncated()).isTrue();
        assertThat(service().getUnmatchedCalls(context, systemId, new HttpLinks.CallFilter("PAYMENTS", null), 50).calls())
            .extracting(site -> site.call().id())
            .containsExactly("b");
        assertThat(service().getUnmatchedCalls(context, systemId, new HttpLinks.CallFilter(null, backend.projectId()), 50).total())
            .isZero();
    }

    @Test
    void knowsAUrlOfOnlyVariablesHasNoText() {
        assertThat(HttpLinkService.hasText("{graphnousApiUrl}{path}")).isFalse();
        assertThat(HttpLinkService.hasText("/{id}/")).isFalse();
        assertThat(HttpLinkService.hasText(null)).isFalse();
        assertThat(HttpLinkService.hasText("{api}/orders")).isTrue();
        assertThat(HttpLinkService.hasText("https://api.stripe.com/v1/charges")).isTrue();
    }

    /**
     * The frontend calls the backend's orders, and nothing its customers;
     * the links are stored with this matching.
     */
    private List<Scan> latestScans() {
        final var scans = List.of(frontend, backend);

        when(scanService.getLatestCompletedScans(context, systemId)).thenReturn(scans);
        when(repository.findCalls(scans)).thenReturn(List.of(call));
        when(repository.findEndpoints(scans)).thenReturn(List.of(endpoint, unused));
        when(repository.findLinkedWith(scans)).thenReturn(Map.of(
            frontend.id(), HttpLinkMatcher.VERSION,
            backend.id(), HttpLinkMatcher.VERSION
        ));
        when(repository.findStoredLinks(scans)).thenReturn(List.of(link));

        return scans;
    }

    private HttpLinkService service() {
        return new HttpLinkService(scanService, scanRepository, projectRepository, repository);
    }

    private static Scan scan() {
        return scan(Project.ProjectId.generate());
    }

    private static Scan scan(final Project.ProjectId projectId) {
        final var now = Instant.now();

        return new Scan(
            Scan.ScanId.generate(), projectId, Scan.ScanStatus.COMPLETED,
            new Scan.SourceRevision("abc123", "main"), now, now, now
        );
    }
}
