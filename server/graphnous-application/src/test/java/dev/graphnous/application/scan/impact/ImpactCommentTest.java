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
            .contains("- Changed field `OrderService.http`");
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
