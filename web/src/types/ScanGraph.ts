/**
 * What a node of a scan's graph is: the scan, what it found in the code,
 * or a library a module depends on.
 */
export type ScanGraphNodeType =
  | "Scan"
  | "ScanTarget"
  | "Module"
  | "File"
  | "Package"
  | "Class"
  | "Method"
  | "Field"
  | "Annotation"
  | "Dependency";

/**
 * The relationship between two nodes, from the source to the target.
 */
export type ScanGraphEdgeType =
  | "HAS_TARGET"
  | "HAS_MODULE"
  | "HAS_FILE"
  | "HAS_PACKAGE"
  | "DECLARES"
  | "CONTAINS"
  | "HAS_METHOD"
  | "HAS_FIELD"
  | "EXTENDS"
  | "IMPLEMENTS"
  | "ANNOTATED_WITH"
  | "DEPENDS_ON";

export interface ScanGraphNode {
  /**
   * What focus takes to center the graph on this node.
   */
  id: string;
  type: ScanGraphNodeType;
  /**
   * What to call it: a name, qualified name or path.
   */
  name: string | null;
  /**
   * The hops from the focus; 0 for the focus itself.
   */
  depth: number;
  /**
   * What the scan found about it, such as a class's qualifiedName, kind
   * and modifiers; they differ per type.
   */
  properties: Record<string, unknown>;
}

export interface ScanGraphEdge {
  /**
   * The id of the node it starts at.
   */
  source: string;
  /**
   * The id of the node it points to.
   */
  target: string;
  type: ScanGraphEdgeType;
  /**
   * Such as the scope of a DEPENDS_ON, or the parameter of an
   * ANNOTATED_WITH.
   */
  properties: Record<string, unknown>;
}

/**
 * The part of a scan's graph around one node: the nodes within some hops
 * of it, nearest first, and the relationships between them.
 */
export interface ScanGraph {
  scanId: string;
  /**
   * The id of the node it is around; the scan's own when none was asked
   * for.
   */
  focus: string;
  depth: number;
  nodes: ScanGraphNode[];
  edges: ScanGraphEdge[];
  /**
   * Whether more nodes were within reach than it holds; the nearest are
   * kept.
   */
  truncated: boolean;
}
