package dev.graphnous.application.scan.impact;

import dev.graphnous.application.scan.graph.ScanGraph;
import dev.graphnous.application.scan.links.HttpLinks;
import dev.graphnous.application.scan.usages.ScanUsages;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImpactCommentTest {

    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

    private final Project.ProjectId api = Project.ProjectId.generate();
    private final Project.ProjectId web = Project.ProjectId.generate();
    private final Map<Project.ProjectId, String> projects = Map.of(api, "api", web, "web");

    private final Scan base = scan("main", "4f1c2d3e4f5a6b7c");
    private final Scan head = scan("orders-v2", "9a8b7c6d5e4f3a2b");

    @Test
    void listsTheBrokenCallsFirstWithWhereTheyAre() {
        final var comment = ImpactComment.render(report(
            List.of(change(ImpactReport.Kind.REMOVED, "method:com.example.OrderController.list()", "list", 1)),
            List.of(new ImpactReport.BrokenCall(
                new HttpLinks.Call(head.id().id() + "|call", head.id(), web, "GET", "/api/orders", "fetch"),
                new HttpLinks.Source("f", "Method", "loadOrders", null, "orders.OrderClient", "src/orders.ts", 12),
                List.of(new HttpLinks.Endpoint("e", base.id(), api, "GET", "/api/orders"))
            )),
            new ImpactReport.Endpoints(
                List.of(new ScanGraph.Snapshot("e", "Endpoint", "GET /api/orders", Map.of())),
                List.of(new ScanGraph.Snapshot("e2", "Endpoint", "GET /api/v2/orders", Map.of()))
            ),
            List.of(new ImpactReport.AffectedProject(web, 1, 1))
        ), projects, null);

        assertThat(comment)
            .startsWith(ImpactComment.MARKER + "\n")
            .contains("`orders-v2` at `9a8b7c6` compared with `main` at `4f1c2d3`.")
            .contains("**1 broken call** · 1 change in use, in 1 place · 1 project affected")
            .contains("| `GET /api/orders` | web | `OrderClient.loadOrders` in `src/orders.ts:12` | `GET /api/orders` |")
            .contains("- Removed `GET /api/orders`", "- Added `GET /api/v2/orders`")
            .contains("| web | 1 | 1 |")
            .doesNotContain("#### Assessment");

        assertThat(sections(comment)).containsExactly(
            "#### Calls that no longer reach an endpoint",
            "#### Changes in use",
            "#### Endpoints",
            "#### Projects affected"
        );
    }

    @Test
    void listsTheChangesInUseWithWhereAndTheRestApart() {
        final var comment = ImpactComment.render(report(
            List.of(
                change(ImpactReport.Kind.REMOVED, "method:com.example.OrderService.create(java.lang.String)", "create", ImpactComment.LISTED_USAGES + 2),
                change(ImpactReport.Kind.CHANGED, "field:src/app/orders.service:OrderService.http", "http", 0)
            ),
            List.of(),
            new ImpactReport.Endpoints(List.of(), List.of()),
            List.of(new ImpactReport.AffectedProject(api, ImpactComment.LISTED_USAGES + 2, 0))
        ), projects, null);

        assertThat(comment)
            .contains("| Method `OrderService.create(String)` | removed | 12 | api |")
            .contains("<details><summary>Where <code>OrderService.create(String)</code> is used</summary>")
            .contains("- `CheckoutController.submit0` calls it, in `src/main/java/Checkout.java:1` (api)")
            .contains("- and 2 more")
            .doesNotContain("submit" + ImpactComment.LISTED_USAGES)
            .contains("<details><summary>1 change nothing uses</summary>")
            .contains("- Field `OrderService.http`, changed");
    }

    @Test
    void headsTheReportWithTheAssessment() {
        final var comment = ImpactComment.render(report(
            List.of(change(ImpactReport.Kind.REMOVED, "method:com.example.OrderService.create()", "create", 1)),
            List.of(),
            new ImpactReport.Endpoints(List.of(), List.of()),
            List.of(new ImpactReport.AffectedProject(api, 1, 0))
        ), projects, "**Risk: low**, only a test uses it.\n");

        assertThat(sections(comment)).first().isEqualTo("#### Assessment");
        assertThat(comment).contains("**Risk: low**, only a test uses it.\n\n<sub>Written by an AI model");
    }

    @Test
    void saysWhenNothingChanged() {
        final var comment = ImpactComment.render(
            report(List.of(), List.of(), new ImpactReport.Endpoints(List.of(), List.of()), List.of()),
            projects,
            "Nothing to assess."
        );

        assertThat(comment)
            .contains("No class, method, field or endpoint changed.")
            .doesNotContain("####");
    }

    @Test
    void leavesOutWhereChangesAreUsedWhenTooLongForAComment() {
        final var changes = IntStream.range(0, ImpactAnalysisService.MAX_CHANGES)
            .mapToObj(i -> change(ImpactReport.Kind.REMOVED, "method:com.example.Service.m" + i + "x".repeat(200) + "()", "m" + i, ImpactAnalysisService.MAX_USAGES))
            .toList();

        final var comment = ImpactComment.render(report(
            changes,
            List.of(),
            new ImpactReport.Endpoints(List.of(), List.of()),
            List.of(new ImpactReport.AffectedProject(api, 1, 0))
        ), projects, null);

        assertThat(comment.length()).isLessThanOrEqualTo(ImpactComment.MAX_LENGTH);
        assertThat(comment).contains("#### Changes in use").doesNotContain("<details><summary>Where");
    }

    @Test
    void saysWhatReplacedAChangeAndCountsThePlacesInTests() {
        final var renamed = new ImpactReport.Change(
            ImpactReport.Kind.RENAMED, base.id().id() + "|server|api|class:x|method:com.example.ScanService.getLatestCompletedScans()",
            base.id(), "Method", "getLatestCompletedScans", List.of(),
            "server|api|class:x|method:com.example.ScanService.getNewestCompletedScans()",
            List.of(), 17, 10, false, Map.of(base.id(), api)
        );
        final var resigned = new ImpactReport.Change(
            ImpactReport.Kind.SIGNATURE_CHANGED, base.id().id() + "|server|api|class:x|method:com.example.Orders.find(java.util.UUID)",
            base.id(), "Method", "find", List.of(), "server|api|class:x|method:com.example.Orders.find(java.lang.String)",
            List.of(), 0, 0, false, Map.of()
        );

        final var comment = ImpactComment.render(new ImpactReport(
            base, head, List.of(), List.of(renamed, resigned),
            new ImpactReport.Endpoints(List.of(), List.of(), List.of(new ImpactReport.Move(
                new ScanGraph.Snapshot("e1", "Endpoint", "GET /api/v1/scans/{head}/impact-comment", Map.of()),
                new ScanGraph.Snapshot("e2", "Endpoint", "GET /api/v1/scans/{head}/pull-request-comment", Map.of())
            ))),
            List.of(), List.of(new ImpactReport.AffectedProject(api, 17, 0)), false
        ), projects, null);

        assertThat(comment)
            .contains("1 change in use, in 17 places (10 in tests)")
            .contains("| Method `ScanService.getLatestCompletedScans()` | renamed to `ScanService.getNewestCompletedScans()` | 17 (10 in tests) |")
            .contains("- Method `Orders.find(UUID)`, signature changed to `Orders.find(String)`")
            .contains("- Moved `GET /api/v1/scans/{head}/impact-comment` to `GET /api/v1/scans/{head}/pull-request-comment`");
    }

    @Test
    void linksThePlacesWhereTheSourcesCan() {
        final var sources = new ImpactComment.Sources() {
            @Override
            public String path(final Scan.ScanId scanId, final String nodeId, final String file) {
                return "server/" + file;
            }

            @Override
            public String link(final Scan.ScanId scanId, final String path, final Integer line) {
                return "https://github.com/acme/shop/blob/abc/" + path + "#L" + line;
            }
        };

        final var comment = ImpactComment.render(report(
            List.of(change(ImpactReport.Kind.REMOVED, "method:com.example.OrderService.create()", "create", 1)),
            List.of(new ImpactReport.BrokenCall(
                new HttpLinks.Call("c", head.id(), web, "GET", "/api/orders", "fetch"),
                new HttpLinks.Source("f", "Method", "loadOrders", null, null, "web/orders.ts", 12),
                List.of(new HttpLinks.Endpoint("e", base.id(), api, "GET", "/api/orders"))
            )),
            new ImpactReport.Endpoints(List.of(), List.of()),
            List.of(new ImpactReport.AffectedProject(api, 1, 0))
        ), projects, null, sources);

        assertThat(comment)
            .contains("calls it, in [`server/src/main/java/Checkout.java:1`](https://github.com/acme/shop/blob/abc/server/src/main/java/Checkout.java#L1)")
            .contains("| `loadOrders` in [`server/web/orders.ts:12`](https://github.com/acme/shop/blob/abc/server/web/orders.ts#L12) |");
    }

    @Test
    void listsTheEndpointsNothingCalls() {
        final var handler = new HttpLinks.Source("h", "Method", "legacy", null, "com.example.Orders", "Orders.java", null);
        final var others = IntStream.range(0, 3)
            .mapToObj(i -> new ImpactReport.UnusedEndpoint(new HttpLinks.Endpoint("o" + i, head.id(), api, "GET", "/api/admin/" + i), null))
            .toList();

        final var comment = ImpactComment.render(new ImpactReport(
            base, head, List.of(),
            List.of(change(ImpactReport.Kind.REMOVED, "method:com.example.Client.loadLegacy()", "loadLegacy", 0)),
            new ImpactReport.Endpoints(List.of(), List.of()),
            new ImpactReport.UnusedEndpoints(
                List.of(new ImpactReport.UnusedEndpoint(new HttpLinks.Endpoint("e1", head.id(), api, "GET", "/api/legacy"), handler)),
                List.of(new ImpactReport.UnusedEndpoint(new HttpLinks.Endpoint("e2", head.id(), api, "POST", "/api/v2/orders"), null)),
                others,
                5
            ),
            List.of(), List.of(), false
        ), projects, null);

        assertThat(comment)
            .contains("· 1 endpoint no longer called")
            .contains("#### Endpoints nothing calls")
            .contains("Clients outside the system may still call them.")
            .contains("**No longer called** after this change:\n\n- `GET /api/legacy`, served by `Orders.legacy` in `Orders.java`")
            .contains("**Not called yet**, added by this change:\n\n- `POST /api/v2/orders`\n")
            .contains("<details><summary>5 other endpoints of the project nothing calls</summary>")
            .contains("- `GET /api/admin/2`\n- and 2 more");
    }

    @Test
    void foldsAwayTheOtherEndpointsNothingCallsWithoutASection() {
        final var comment = ImpactComment.render(new ImpactReport(
            base, head, List.of(),
            List.of(change(ImpactReport.Kind.REMOVED, "method:com.example.A.b()", "b", 1)),
            new ImpactReport.Endpoints(List.of(), List.of()),
            new ImpactReport.UnusedEndpoints(List.of(), List.of(),
                List.of(new ImpactReport.UnusedEndpoint(new HttpLinks.Endpoint("o", head.id(), api, "GET", "/api/admin"), null)), 1),
            List.of(), List.of(new ImpactReport.AffectedProject(api, 1, 0)), false
        ), projects, null);

        assertThat(sections(comment)).doesNotContain("#### Endpoints nothing calls");
        assertThat(comment).contains("<details><summary>1 other endpoint of the project nothing calls</summary>");
    }

    @Test
    void namesClassesMethodsAndFieldsWithoutPackagesOrFiles() {
        assertThat(ImpactComment.label("s|server|api|class:com.example.Order|method:com.example.Order.total(java.util.List)", "total"))
            .isEqualTo("Order.total(List)");
        assertThat(ImpactComment.label("s|web|.|class:src/app/orders.service:OrderService", "OrderService"))
            .isEqualTo("OrderService");
        assertThat(ImpactComment.label("no type", "name")).isEqualTo("name");
    }

    @Test
    void saysWhenTheReportIsTruncated() {
        final var comment = ImpactComment.render(new ImpactReport(
            base, head, List.of(),
            List.of(change(ImpactReport.Kind.REMOVED, "method:com.example.A.b()", "b", 0)),
            new ImpactReport.Endpoints(List.of(), List.of()), List.of(), List.of(), true
        ), projects, null);

        assertThat(comment).contains("it lists the first " + ImpactAnalysisService.MAX_CHANGES);
    }

    private ImpactReport report(
        final List<ImpactReport.Change> changes,
        final List<ImpactReport.BrokenCall> brokenCalls,
        final ImpactReport.Endpoints endpoints,
        final List<ImpactReport.AffectedProject> affected
    ) {
        return new ImpactReport(base, head, List.of(), changes, endpoints, brokenCalls, affected, false);
    }

    private ImpactReport.Change change(final ImpactReport.Kind kind, final String key, final String name, final int usages) {
        final var scan = kind == ImpactReport.Kind.REMOVED ? base : head;
        final var found = IntStream.range(0, Math.min(usages, ImpactAnalysisService.MAX_USAGES))
            .mapToObj(i -> new ScanUsages.Usage(
                "CALLS", new ScanUsages.Node("u" + i, "Method", "submit" + i, null), "com.example.CheckoutController",
                "src/main/java/Checkout.java", i + 1, new ScanUsages.Node("t", "Method", name, null), null, 1, List.of(),
                scan.id(), null
            ))
            .toList();

        return new ImpactReport.Change(
            kind, scan.id().id() + "|server|api|class:x|" + key, scan.id(), key.startsWith("field:") ? "Field" : "Method",
            name, List.of(), found, usages, false, Map.of(scan.id(), api)
        );
    }

    private Scan scan(final String branch, final String revision) {
        return new Scan(Scan.ScanId.generate(), api, Scan.ScanStatus.COMPLETED, new Scan.SourceRevision(revision, branch), NOW, NOW, NOW);
    }

    private static List<String> sections(final String comment) {
        return comment.lines().filter(line -> line.startsWith("#### ")).toList();
    }
}
