/**
 * A node of a scan's graph, as much of it as a usage shows.
 */
export interface ScanUsageNode {
  /** Its id in the scan's graph, which focuses the graph on it */
  id: string;
  /** Class, Method or Field */
  type: string;
  name: string | null;
  qualifiedName: string | null;
}

/**
 * How a node is used: by what a body does, or by a class that extends or
 * implements it.
 */
export type ScanUsageRelationship =
  | "CALLS"
  | "INSTANTIATES"
  | "READS"
  | "WRITES"
  | "USES"
  | "EXTENDS"
  | "IMPLEMENTS"
  // A method of a subclass with the name of the one asked about
  | "OVERRIDES"
  // A method or function that calls, over HTTP, an endpoint the target serves
  | "REQUESTS";

/**
 * An HTTP call of an endpoint the target serves.
 */
export interface ScanUsageRequest {
  /** What the call requests with, such as GET; null when the scan does not say */
  callMethod: string | null;
  /** The url the call requests, as written, with what is only known when it runs in braces */
  callUrl: string | null;
  /** The endpoint's method, or ANY */
  endpointMethod: string | null;
  /** The endpoint's path, such as /api/orders/{id} */
  endpointUrl: string | null;
}

/**
 * One thing a call passes, as written where it is called.
 */
export interface ScanUsageArgument {
  /**
   * The name it is passed under, such as the attribute of a rendered
   * component; null when passed by position.
   */
  name: string | null;
  /** The expression passed, as written; a long one is cut off and ends in … */
  value: string;
  /** Whether it is spread, as ...ids or {...props}, so what it passes is not known */
  spread: boolean;
}

/**
 * One place that uses what was asked about, or one of its members.
 */
export interface ScanUsage {
  relationship: ScanUsageRelationship;
  /** The method, function or class that uses it */
  user: ScanUsageNode;
  /** The name of the class the user is a method of; null for a function or a class */
  owner: string | null;
  /** The path of the file the user is in, if known */
  file: string | null;
  /** The line it uses it on; null when the scan does not say */
  line: number | null;
  /**
   * What it uses: the subject, or a method or field of it; or, through
   * inheritance, the method of that name of a class above or below it.
   */
  target: ScanUsageNode;
  /** The name of the class the target is a method or field of */
  targetOwner: string | null;
  /** 1 for what uses it itself; 2 for what calls that, and so on */
  depth: number;
  /**
   * What a call or an instantiation passes, in the order written, empty
   * when it passes nothing; null for another relationship, and when the
   * scan did not record the arguments of calls.
   */
  arguments: ScanUsageArgument[] | null;
  /**
   * The scan the user is in: the one asked about, or over HTTP that of
   * another project of its system.
   */
  scanId: string;
  /** The project of that scan */
  projectId: string | null;
  /** For REQUESTS, the call and the endpoint it requests; null otherwise */
  request: ScanUsageRequest | null;
}

/**
 * What uses a node of a scan: what a change to it affects.
 */
export interface ScanUsages {
  scanId: string;
  /**
   * What was asked about: for what an enhancer added, such as a component,
   * the function or class it is of.
   */
  subject: ScanUsageNode;
  /** By file and line */
  usages: ScanUsage[];
  /** Whether there are more usages than it holds */
  truncated: boolean;
}
