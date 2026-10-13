/**
 * An HTTP call an enhancer found in a scan, such as a call of fetch.
 */
export interface SystemLinkCall {
  /** Its id in its scan's graph, which focuses the graph on it */
  id: string;
  scanId: string;
  projectId: string;
  /** Null when the scan does not say */
  httpMethod: string | null;
  /** As written, with what is only known when it runs in braces */
  url: string | null;
  /** What makes the call, such as fetch */
  client: string | null;
}

/**
 * An endpoint an enhancer found in a scan.
 */
export interface SystemLinkEndpoint {
  id: string;
  scanId: string;
  projectId: string;
  /** Such as GET, or ANY */
  httpMethod: string;
  url: string;
}

/**
 * A call that requests an endpoint, as far as their urls and methods say.
 */
export interface SystemLink {
  call: SystemLinkCall;
  endpoint: SystemLinkEndpoint;
  /**
   * PATH when the call's whole path is the endpoint's; SUFFIX when it is
   * the end of it, for a call that starts with a base only known when it
   * runs.
   */
  match: "PATH" | "SUFFIX";
  /** Whether the call and the endpoint are of different projects */
  crossesProjects: boolean;
}

/**
 * How scans are linked over HTTP, with how much there was to link.
 */
export interface SystemLinks {
  links: SystemLink[];
  /** How many HTTP calls the scans have, linked or not */
  calls: number;
  /** How many endpoints the scans have, linked or not */
  endpoints: number;
}
