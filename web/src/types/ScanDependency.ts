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

/**
 * A library a module of one of a system's projects depends on, as the
 * project's latest completed scan found it.
 */
export interface SystemDependency extends ScanDependency {
  /**
   * The project whose module depends on it.
   */
  projectId: string;
  /**
   * The scan of the project it was found in.
   */
  scanId: string;
}

/**
 * A library the projects of a system depend on, with every version of it
 * they use: more than one version is drift between them.
 */
export interface SystemLibrary {
  /**
   * Such as org.slf4j:slf4j-api.
   */
  name: string;
  versions: SystemLibraryVersion[];
}

export interface SystemLibraryVersion {
  /**
   * Null when the library is used without a version.
   */
  version: string | null;
  /**
   * The ids of the projects with a module that depends on it.
   */
  projects: string[];
}

/**
 * What the libraries of a system can be sorted by: their name, or how many
 * versions of them are in use.
 */
export type SystemLibrarySort = "name" | "versions";
