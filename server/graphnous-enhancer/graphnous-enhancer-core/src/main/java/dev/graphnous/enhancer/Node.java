package dev.graphnous.enhancer;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A node an enhancer adds to a scan's graph, for the node of the scan
 * result it enhances.
 * <p>
 * Ids are unique within the scan and extend the id of their parent, as the
 * nodes of the scan result do, e.g. {@code backend|orders|class:com.example.Order}
 * for a class of module {@code orders} in target {@code backend}. An enhancer
 * adds its own nodes below the node they enhance, such as
 * {@code backend|orders|class:com.example.Order|controller}.
 *
 * @param id       the id of the node, unique within the scan
 * @param sourceId the id of the node it enhances, which gets an
 *                 {@code ENHANCE} relationship to it; null for none
 * @param labels   the node's labels
 * @param metadata the node's properties; values may be null
 */
public record Node(
    String id,
    String sourceId,
    List<String> labels,
    Map<String, Object> metadata
) {

    public Node {
        labels = List.copyOf(labels);
        metadata = Collections.unmodifiableMap(new HashMap<>(metadata));
    }
}
