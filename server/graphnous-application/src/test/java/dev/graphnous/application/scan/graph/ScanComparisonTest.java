package dev.graphnous.application.scan.graph;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScanComparisonTest {

    @Test
    void findsTheNodesOnlyOneScanHas() {
        final var comparison = ScanComparison.compare(
            List.of(node("orders|class:Order", "Class"), node("orders|class:Legacy", "Class")),
            List.of(node("orders|class:Order", "Class"), node("orders|class:Invoice", "Class")),
            10
        );

        assertThat(comparison.added()).extracting(ScanGraph.Snapshot::key).containsExactly("orders|class:Invoice");
        assertThat(comparison.removed()).extracting(ScanGraph.Snapshot::key).containsExactly("orders|class:Legacy");
        assertThat(comparison.changed()).isEmpty();
        assertThat(comparison.truncated()).isFalse();
    }

    @Test
    void listsThePropertiesThatDiffer() {
        final var comparison = ScanComparison.compare(
            List.of(node("orders|class:Order", "Class", Map.of(
                "kind", "CLASS",
                "modifiers", List.of("PUBLIC"),
                "superClass", "Entity"
            ))),
            List.of(node("orders|class:Order", "Class", Map.of(
                "kind", "CLASS",
                "modifiers", List.of("PUBLIC", "FINAL"),
                "interfaces", List.of("Identified")
            ))),
            10
        );

        assertThat(comparison.added()).isEmpty();
        assertThat(comparison.removed()).isEmpty();
        assertThat(comparison.changed()).singleElement().satisfies(change -> {
            assertThat(change.key()).isEqualTo("orders|class:Order");
            assertThat(change.properties()).containsExactly(
                new ScanGraph.PropertyChange("interfaces", null, List.of("Identified")),
                new ScanGraph.PropertyChange("modifiers", List.of("PUBLIC"), List.of("PUBLIC", "FINAL")),
                new ScanGraph.PropertyChange("superClass", "Entity", null)
            );
        });
    }

    @Test
    void ordersByTypeFromTargetsDownThenByKey() {
        final var comparison = ScanComparison.compare(List.of(), List.of(
            node("orders|class:B", "Class"),
            node("orders|dependency:slf4j", "Dependency"),
            node("orders|class:A", "Class"),
            node("orders|class:A|method:a()", "Method"),
            node("orders", "Module")
        ), 10);

        assertThat(comparison.added()).extracting(ScanGraph.Snapshot::key).containsExactly(
            "orders", "orders|class:A", "orders|class:B", "orders|class:A|method:a()", "orders|dependency:slf4j"
        );
        assertThat(comparison.summary()).containsExactly(
            new ScanGraph.TypeChanges("Module", 1, 0, 0),
            new ScanGraph.TypeChanges("Class", 2, 0, 0),
            new ScanGraph.TypeChanges("Method", 1, 0, 0),
            new ScanGraph.TypeChanges("Dependency", 1, 0, 0)
        );
    }

    @Test
    void countsEveryChangeButListsUpToTheLimit() {
        final var head = new ArrayList<ScanGraph.Snapshot>();

        for (int i = 0; i < 5; i++) {
            head.add(node("orders|class:C" + i, "Class"));
        }

        final var comparison = ScanComparison.compare(List.of(), head, 3);

        assertThat(comparison.added()).hasSize(3);
        assertThat(comparison.summary()).containsExactly(new ScanGraph.TypeChanges("Class", 5, 0, 0));
        assertThat(comparison.truncated()).isTrue();
    }

    @Test
    void listsOnlyTheTypesAskedForButCountsEvery() {
        final var head = new ArrayList<ScanGraph.Snapshot>();
        head.add(node("orders|file:Order.java", "File"));

        for (int i = 0; i < 3; i++) {
            head.add(node("orders|class:Order|method:m" + i, "Method"));
        }

        final var comparison = ScanComparison.compare(List.of(node("orders|class:Legacy", "Class")), head, Set.of("Method"), 2);

        assertThat(comparison.added()).extracting(ScanGraph.Snapshot::key)
            .containsExactly("orders|class:Order|method:m0", "orders|class:Order|method:m1");
        assertThat(comparison.removed()).isEmpty();
        assertThat(comparison.summary()).containsExactly(
            new ScanGraph.TypeChanges("File", 1, 0, 0),
            new ScanGraph.TypeChanges("Class", 0, 1, 0),
            new ScanGraph.TypeChanges("Method", 3, 0, 0)
        );
        // Of the methods asked for
        assertThat(comparison.truncated()).isTrue();
    }

    @Test
    void listsTheEndpointsAfterTheFieldsAndBeforeTheDependencies() {
        final var comparison = ScanComparison.compare(List.of(), List.of(
            node("orders|dependency:slf4j", "Dependency"),
            node("orders|class:A|method:a|endpoint:GET /a", "Endpoint"),
            node("orders|class:A|field:f", "Field")
        ), 10);

        assertThat(comparison.added()).extracting(ScanGraph.Snapshot::type).containsExactly("Field", "Endpoint", "Dependency");
    }

    @Test
    void findsNothingBetweenEqualScans() {
        final var nodes = List.of(node("orders", "Module", Map.of("name", "orders")));

        final var comparison = ScanComparison.compare(nodes, nodes, 10);

        assertThat(comparison.summary()).isEmpty();
        assertThat(comparison.added()).isEmpty();
        assertThat(comparison.removed()).isEmpty();
        assertThat(comparison.changed()).isEmpty();
    }

    private static ScanGraph.Snapshot node(final String key, final String type) {
        return node(key, type, Map.of());
    }

    private static ScanGraph.Snapshot node(final String key, final String type, final Map<String, Object> properties) {
        return new ScanGraph.Snapshot(key, type, key.substring(key.lastIndexOf(':') + 1), properties);
    }
}
