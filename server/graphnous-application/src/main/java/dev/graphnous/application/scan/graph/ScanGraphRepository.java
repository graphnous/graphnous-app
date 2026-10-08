package dev.graphnous.application.scan.graph;

import dev.graphnous.domain.scan.Scan;

import java.util.List;
import java.util.Optional;

/**
 * Reads what a scan found from its graph.
 */
public interface ScanGraphRepository {

    ScanGraph.Overview getOverview(Scan.ScanId scanId);

    /**
     * The nodes of the scan within depth hops of the focus, following
     * relationships either way, and the relationships between them; at most
     * limit nodes, the nearest first. Empty when the scan has no node with
     * the focus' id.
     *
     * @param focus the id of a node of the scan; the scan itself when null
     */
    Optional<ScanGraph.Neighbourhood> findNeighbourhood(Scan.ScanId scanId, String focus, int depth, int limit);

    /**
     * The classes whose name or qualified name contains the query, ignoring
     * case, by qualified name.
     */
    List<ScanGraph.ClassSummary> findClasses(Scan.ScanId scanId, String query, int limit);

    /**
     * The class with this qualified name; the first by module when several
     * modules declare one.
     */
    Optional<ScanGraph.ClassDetails> findClass(Scan.ScanId scanId, String qualifiedName);

    /**
     * The classes, methods and fields with an annotation whose name or
     * qualified name is this one, ignoring case.
     */
    List<ScanGraph.AnnotatedElement> findAnnotated(Scan.ScanId scanId, String annotation, int limit);

    /**
     * The dependencies of every module, by module and name.
     */
    List<ScanGraph.Dependency> findDependencies(Scan.ScanId scanId);

    /**
     * Every target, module, package, file, class, method and field of the
     * scan, and every dependency of its modules, to compare with another
     * scan's; empty for a scan without results.
     */
    List<ScanGraph.Snapshot> findSnapshots(Scan.ScanId scanId);

}
