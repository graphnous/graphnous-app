package dev.graphnous.application.scan.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Compares the nodes of two scans by their keys: a node only the head scan
 * has was added, one only the base scan has was removed, and one both have
 * changed when any of its properties differ.
 */
final class ScanComparison {

    /**
     * The order the types are listed in: from the targets down to the
     * fields, then the dependencies; any other type after those.
     */
    static final List<String> TYPES = List.of(
        "ScanTarget", "Module", "Package", "File", "Class", "Method", "Field", "Dependency"
    );

    private static final Comparator<String> BY_TYPE = Comparator.comparingInt(type -> {
        final var index = TYPES.indexOf(type);

        return index < 0 ? TYPES.size() : index;
    });

    private ScanComparison() {
    }

    /**
     * @param limit the most nodes each of added, removed and changed holds
     */
    static ScanGraph.Comparison compare(
        final List<ScanGraph.Snapshot> base,
        final List<ScanGraph.Snapshot> head,
        final int limit
    ) {
        final var before = byKey(base);
        final var after = byKey(head);

        final var added = new ArrayList<ScanGraph.Snapshot>();
        final var removed = new ArrayList<ScanGraph.Snapshot>();
        final var changed = new ArrayList<ScanGraph.Change>();

        after.forEach((key, node) -> {
            final var old = before.get(key);

            if (old == null) {
                added.add(node);
                return;
            }

            final var properties = changes(old.properties(), node.properties());

            if (!properties.isEmpty()) {
                changed.add(new ScanGraph.Change(key, node.type(), node.name(), properties));
            }
        });

        before.forEach((key, node) -> {
            if (!after.containsKey(key)) {
                removed.add(node);
            }
        });

        final var snapshotOrder = Comparator.comparing(ScanGraph.Snapshot::type, BY_TYPE)
            .thenComparing(ScanGraph.Snapshot::key);

        added.sort(snapshotOrder);
        removed.sort(snapshotOrder);
        changed.sort(Comparator.comparing(ScanGraph.Change::type, BY_TYPE).thenComparing(ScanGraph.Change::key));

        return new ScanGraph.Comparison(
            summary(added, removed, changed),
            first(added, limit),
            first(removed, limit),
            first(changed, limit),
            added.size() > limit || removed.size() > limit || changed.size() > limit
        );
    }

    /**
     * The nodes by key; a key listed twice keeps its first node.
     */
    private static Map<String, ScanGraph.Snapshot> byKey(final List<ScanGraph.Snapshot> nodes) {
        final var byKey = new LinkedHashMap<String, ScanGraph.Snapshot>();

        nodes.forEach(node -> byKey.putIfAbsent(node.key(), node));

        return byKey;
    }

    /**
     * The properties whose values differ, by name; a property only one side
     * has is null on the other.
     */
    private static List<ScanGraph.PropertyChange> changes(
        final Map<String, Object> before,
        final Map<String, Object> after
    ) {
        final var names = new TreeSet<String>();
        names.addAll(before.keySet());
        names.addAll(after.keySet());

        return names.stream()
            .filter(name -> !Objects.equals(before.get(name), after.get(name)))
            .map(name -> new ScanGraph.PropertyChange(name, before.get(name), after.get(name)))
            .toList();
    }

    private static List<ScanGraph.TypeChanges> summary(
        final List<ScanGraph.Snapshot> added,
        final List<ScanGraph.Snapshot> removed,
        final List<ScanGraph.Change> changed
    ) {
        final var types = new TreeSet<>(BY_TYPE.thenComparing(Comparator.naturalOrder()));
        added.forEach(node -> types.add(node.type()));
        removed.forEach(node -> types.add(node.type()));
        changed.forEach(node -> types.add(node.type()));

        return types.stream()
            .map(type -> new ScanGraph.TypeChanges(
                type,
                (int) added.stream().filter(node -> node.type().equals(type)).count(),
                (int) removed.stream().filter(node -> node.type().equals(type)).count(),
                (int) changed.stream().filter(node -> node.type().equals(type)).count()
            ))
            .toList();
    }

    private static <T> List<T> first(final List<T> list, final int limit) {
        return List.copyOf(list.subList(0, Math.min(list.size(), limit)));
    }
}
