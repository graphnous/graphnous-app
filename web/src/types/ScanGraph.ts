/**
 * What a node of a scan's graph is: the scan, what it found in the code,
 * a library a module depends on, an enhancer that ran on the scan, or
 * what an enhancer added. Enhancers may add types that are not listed
 * here yet; those come as they are.
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
  | "Dependency"
  // An enhancer that ran on the scan
  | "Enhancement"
  // The building blocks of a user interface, of React and Angular
  | "Component"
  | "Directive"
  | "NgModule"
  // What an application is wired from, of Spring and Angular
  | "Application"
  | "Configuration"
  | "Controller"
  | "Service"
  | "Repository"
  | "Bean"
  // What is served and called over HTTP
  | "Route"
  | "Endpoint"
  | "PathVariable"
  | "HttpCall";

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
  | "DEPENDS_ON"
  // An enhancer and what it added, each from the node it enhances
  | "HAS_ENHANCEMENT"
  | "ADDED"
  | "ENHANCE"
  // Between what enhancers added
  | "RENDERS"
  | "IMPORTS"
  | "INJECTS"
  | "HAS_ENDPOINT"
  | "HAS_PATH_VARIABLE"
  | "HAS_HTTP_CALL"
  // Found between scans, not stored in any: a project whose HTTP calls
  // request endpoints of another, and a call that requests an endpoint
  | "CALLS_ENDPOINTS_OF"
  | "REQUESTS"
  // What the body of a method or function uses, in the usages layer: one
  // for each kind of use between two nodes, with how many places
  | "CALLS"
  | "INSTANTIATES"
  | "READS"
  | "WRITES"
  | "USES";

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
 * A part of a scan's graph that shows or hides as one: what the scan itself
 * found, or what one enhancer added to it.
 */
export interface ScanGraphLayer {
  /**
   * "result" for what the scan found; the enhancer's name, an @ and its
   * version for what it added, such as spring@1.0.0; "usages" for what
   * the bodies of methods use, which shows only when asked for.
   */
  id: string;
  /**
   * What to call it, such as spring 1.0.0.
   */
  name: string;
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
  /**
   * Every layer of the scan's graph, shown or not: what the scan found,
   * then what each enhancer added.
   */
  layers: ScanGraphLayer[];
}
