/**
 * What a compared node is: a node of a scan's graph, or a module's
 * dependency on a library.
 */
export type ComparedNodeType =
  | "ScanTarget"
  | "Module"
  | "Package"
  | "File"
  | "Class"
  | "Method"
  | "Field"
  | "Dependency";

/**
 * A node only one of the compared scans has.
 */
export interface ComparedNode {
  /**
   * What identifies it in both scans: its id in a scan's graph without the
   * scan's id and the `|` after it.
   */
  key: string;
  type: ComparedNodeType;
  name: string | null;
  properties: Record<string, unknown>;
}

export interface ChangedProperty {
  name: string;
  /** Its value in the base scan; null when it had none */
  before: unknown;
  /** Its value in the head scan; null when it has none */
  after: unknown;
}

/**
 * A node both scans have, with the properties that differ.
 */
export interface ChangedNode {
  key: string;
  type: ComparedNodeType;
  name: string | null;
  properties: ChangedProperty[];
}

export interface ScanComparisonSummary {
  type: ComparedNodeType;
  added: number;
  removed: number;
  changed: number;
}

/**
 * How the head scan's graph differs from the base scan's.
 */
export interface ScanComparison {
  base: string;
  head: string;
  /**
   * How many nodes of each type with any change were added, removed and
   * changed, of all of them.
   */
  summary: ScanComparisonSummary[];
  added: ComparedNode[];
  removed: ComparedNode[];
  changed: ChangedNode[];
  /**
   * Whether a list holds fewer nodes than the summary counts.
   */
  truncated: boolean;
}
