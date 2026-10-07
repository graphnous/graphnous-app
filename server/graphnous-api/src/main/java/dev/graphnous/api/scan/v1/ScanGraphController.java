package dev.graphnous.api.scan.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.scan.graph.ScanGraph;
import dev.graphnous.application.scan.graph.ScanGraphService;
import dev.graphnous.domain.scan.Scan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The graph a scan found, a part at a time: the nodes around a focused node
 * and the relationships between them, to draw and to explore from by
 * focusing on another node.
 * <p>
 * Not in the published OpenAPI spec yet, so its models are its own.
 */
@RestController
@RequestMapping("/api/v1/scans")
public class ScanGraphController {

    private final RequestContextProvider contextProvider;
    private final ScanGraphService scanGraphService;

    public ScanGraphController(
        final RequestContextProvider contextProvider,
        final ScanGraphService scanGraphService
    ) {
        this.contextProvider = contextProvider;
        this.scanGraphService = scanGraphService;
    }

    /**
     * @param focus     the id of the node the graph is around
     * @param depth     the hops from the focus it reaches
     * @param truncated whether more nodes were within reach than it holds;
     *                  the nearest are kept
     */
    public record ScanGraphResponse(
        UUID scanId,
        String focus,
        int depth,
        List<ScanGraphNode> nodes,
        List<ScanGraphEdge> edges,
        boolean truncated
    ) {
    }

    /**
     * @param id         what focus takes to center the graph on this node
     * @param type       its kind: Scan, ScanTarget, Module, File, Package,
     *                   Class, Method, Field, Annotation or Dependency
     * @param depth      the hops from the focus
     * @param properties what the scan found about it
     */
    public record ScanGraphNode(
        String id,
        String type,
        String name,
        int depth,
        Map<String, Object> properties
    ) {
    }

    /**
     * @param source the id of the node it starts at
     * @param target the id of the node it points to
     * @param type   the relationship, such as HAS_MODULE, DECLARES, EXTENDS
     *               or DEPENDS_ON
     */
    public record ScanGraphEdge(
        String source,
        String target,
        String type,
        Map<String, Object> properties
    ) {
    }

    /**
     * Without a focus the graph is around the scan itself, two hops deep by
     * default: its targets and their modules. With one, it is that node and
     * its neighbours, one hop deep by default. A depth goes up to 5.
     */
    @GetMapping("/{id}/graph")
    public ResponseEntity<ScanGraphResponse> getGraph(
        @PathVariable("id") final UUID id,
        @RequestParam(name = "focus", required = false) final String focus,
        @RequestParam(name = "depth", required = false) final Integer depth
    ) {
        final var neighbourhood = scanGraphService.getNeighbourhood(
            contextProvider.get(),
            new Scan.ScanId(id),
            focus,
            depth
        );

        return ResponseEntity.ok(fromDomain(id, neighbourhood));
    }

    private static ScanGraphResponse fromDomain(final UUID scanId, final ScanGraph.Neighbourhood neighbourhood) {
        return new ScanGraphResponse(
            scanId,
            neighbourhood.focus(),
            neighbourhood.depth(),
            neighbourhood.nodes().stream()
                .map(node -> new ScanGraphNode(node.id(), node.type(), node.name(), node.depth(), node.properties()))
                .toList(),
            neighbourhood.edges().stream()
                .map(edge -> new ScanGraphEdge(edge.source(), edge.target(), edge.type(), edge.properties()))
                .toList(),
            neighbourhood.truncated()
        );
    }
}
