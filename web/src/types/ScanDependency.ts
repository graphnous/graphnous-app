/**
 * A library a module of a scan depends on.
 */
export interface ScanDependency {
  /**
   * The library's id in the scan's graph, which focuses the graph on it.
   */
  id: string;
  /**
   * The path of the module that depends on it.
   */
  module: string;
  /**
   * Such as org.slf4j:slf4j-api.
   */
  name: string;
  version?: string | null;
  /**
   * Such as compile or test, if the build system has scopes.
   */
  scope?: string | null;
}

/**
 * What the dependencies can be sorted by.
 */
export type ScanDependencySort = "name" | "module" | "scope";
