package dev.graphnous.application.scan.links;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class HttpLinkMatcherTest {

    private final Scan.ScanId frontendScan = Scan.ScanId.generate();
    private final Project.ProjectId frontend = Project.ProjectId.generate();

    private final Scan.ScanId backendScan = Scan.ScanId.generate();
    private final Project.ProjectId backend = Project.ProjectId.generate();

    @Test
    void linksACallToTheEndpointOfTheSamePath() {
        assertThat(endpointsOf("/api/orders", "GET /api/orders", "GET /api/customers")).containsExactly("GET /api/orders");
    }

    @Test
    void takesAVariableOfTheEndpointForAnySegment() {
        assertThat(endpointsOf("/api/orders/42", "GET /api/orders/{id}")).containsExactly("GET /api/orders/{id}");
        assertThat(endpointsOf("/api/orders/{order.id}/lines", "GET /api/orders/{id}/lines"))
            .containsExactly("GET /api/orders/{id}/lines");
        // Spring variables with a pattern, and its wildcard; both fit as well
        assertThat(endpointsOf("/api/orders/42", "GET /api/orders/{id:\\d+}", "GET /api/*/42"))
            .containsExactly("GET /api/orders/{id:\\d+}", "GET /api/*/42");
    }

    @Test
    void doesNotTakeAVariableOfTheCallForAFixedPath() {
        assertThat(endpointsOf("/api/orders/{id}", "GET /api/orders/latest")).isEmpty();
        assertThat(endpointsOf("/api/{resource}", "GET /api/orders", "GET /api/customers")).isEmpty();
    }

    @Test
    void linksNothingWithoutTextInCommon() {
        assertThat(endpointsOf("{url}", "GET /api/orders")).isEmpty();
        assertThat(endpointsOf("/{a}/{b}", "GET /{x}/{y}")).isEmpty();
        assertThat(endpointsOf(null, "GET /api/orders")).isEmpty();
        assertThat(endpointsOf("  ", "GET /api/orders")).isEmpty();
    }

    @Test
    void needsThePathsToBeAsLong() {
        assertThat(endpointsOf("/api/orders", "GET /api/orders/{id}", "GET /api")).isEmpty();
    }

    @Test
    void takesTheRestOfAnEndpointForWhateverIsLeft() {
        assertThat(endpointsOf("/api/files/a/b/c.txt", "GET /api/files/{*path}")).containsExactly("GET /api/files/{*path}");
        // Also for nothing, as an optional catch-all of Next.js
        assertThat(endpointsOf("/api/docs", "GET /api/docs/{*slug}")).containsExactly("GET /api/docs/{*slug}");
        assertThat(endpointsOf("/static/css/site.css", "GET /static/**")).containsExactly("GET /static/**");
    }

    @Test
    void leavesTheHostQueryAndFragmentOut() {
        assertThat(endpointsOf("https://shop.example.com/api/orders?page={page}#top", "GET /api/orders"))
            .containsExactly("GET /api/orders");
        assertThat(endpointsOf("/api/orders/?status=open", "GET /api/orders")).containsExactly("GET /api/orders");
        // A ? in a variable's expression is not a query
        assertThat(endpointsOf("/api/orders/{open ? 'open' : 'closed'}", "GET /api/orders/{status}"))
            .containsExactly("GET /api/orders/{status}");
    }

    @Test
    void takesABaseOnlyKnownWhenItRunsForTheStartOfThePath() {
        final var links = links(
            List.of(call("{API_URL}/orders/{id}", null)),
            List.of(endpoint("GET /api/v1/orders/{id}"), endpoint("GET /api/v1/customers/{id}"))
        );

        assertThat(links)
            .extracting(link -> link.endpoint().url(), HttpLinks.Link::match)
            .containsExactly(tuple("/api/v1/orders/{id}", HttpLinks.Match.SUFFIX));

        // The whole path, when the base holds none of it
        assertThat(links(List.of(call("{API_URL}/orders", null)), List.of(endpoint("GET /orders"))))
            .extracting(HttpLinks.Link::match)
            .containsExactly(HttpLinks.Match.PATH);
        // Not without a base
        assertThat(endpointsOf("/orders/{id}", "GET /api/v1/orders/{id}")).isEmpty();
        // The base holds text, not a variable
        assertThat(endpointsOf("{API_URL}/orders", "GET /{tenant}/orders")).isEmpty();
    }

    @Test
    void needsTheSameMethodWhenBothSayTheirs() {
        final var endpoints = List.of(endpoint("GET /api/orders"), endpoint("POST /api/orders"), endpoint("ANY /api/orders"));

        assertThat(links(List.of(call("/api/orders", "post")), endpoints))
            .extracting(link -> link.endpoint().httpMethod())
            .containsExactly("POST", "ANY");
        // A call that does not say its method may be of any
        assertThat(links(List.of(call("/api/orders", null)), endpoints)).hasSize(3);
    }

    @Test
    void prefersTheEndpointThatFitsBestOfEachScan() {
        final var other = Scan.ScanId.generate();
        final var otherProject = Project.ProjectId.generate();

        final var links = links(
            List.of(call("/api/orders/latest", "GET")),
            List.of(
                endpoint("GET /api/orders/{id}"),
                endpoint("GET /api/orders/latest"),
                // The only one of another scan, so the best of it
                new HttpLinks.Endpoint("other", other, otherProject, "GET", "/api/orders/{id}")
            )
        );

        assertThat(links)
            .extracting(link -> link.endpoint().scanId(), link -> link.endpoint().url())
            .containsExactly(
                tuple(backendScan, "/api/orders/latest"),
                tuple(other, "/api/orders/{id}")
            );
    }

    @Test
    void saysWhetherALinkCrossesProjects() {
        final var own = new HttpLinks.Endpoint("own", frontendScan, frontend, "GET", "/api/orders");

        final var links = links(List.of(call("/api/orders", "GET")), List.of(own, endpoint("GET /api/orders")));

        assertThat(links).extracting(HttpLinks.Link::crossesProjects).containsExactly(false, true);
    }

    /**
     * The endpoints, as "GET /path", that a call of the url is of.
     */
    private List<String> endpointsOf(final String url, final String... endpoints) {
        return links(List.of(call(url, "GET")), List.of(endpoints).stream().map(this::endpoint).toList())
            .stream()
            .map(link -> link.endpoint().httpMethod() + " " + link.endpoint().url())
            .toList();
    }

    private static List<HttpLinks.Link> links(final List<HttpLinks.Call> calls, final List<HttpLinks.Endpoint> endpoints) {
        return HttpLinkMatcher.match(calls, endpoints);
    }

    private HttpLinks.Call call(final String url, final String httpMethod) {
        return new HttpLinks.Call("call:" + url, frontendScan, frontend, httpMethod, url, "fetch");
    }

    private HttpLinks.Endpoint endpoint(final String methodAndUrl) {
        final var parts = methodAndUrl.split(" ", 2);

        return new HttpLinks.Endpoint("endpoint:" + methodAndUrl, backendScan, backend, parts[0], parts[1]);
    }
}
