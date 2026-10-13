/**
 * What of a scan's code can be listed.
 */
export type ScanCodeKind = "modules" | "packages" | "files" | "classes" | "methods";

/**
 * A module of a scan, with how much it holds.
 */
export interface ScanModule {
  /** Its id in the scan's graph, which focuses the graph on it */
  id: string;
  /** The path of the target it is of */
  target: string;
  name: string | null;
  path: string;
  files: number;
  packages: number;
  classes: number;
  methods: number;
  dependencies: number;
}

export interface ScanPackage {
  id: string;
  /** The path of the module it is of */
  module: string;
  name: string | null;
  qualifiedName: string;
  classes: number;
  functions: number;
  variables: number;
}

export interface ScanFile {
  id: string;
  target: string;
  module: string;
  path: string;
  language: string | null;
  /** MAIN or TEST */
  sourceSet: string | null;
  /** In bytes */
  size: number | null;
  checksum: string | null;
  classes: number;
  functions: number;
}

export interface ScanClass {
  id: string;
  name: string;
  qualifiedName: string;
  /** Such as CLASS, INTERFACE or ENUM */
  kind: string | null;
  modifiers: string[];
  module: string;
  file: string | null;
  packageName: string | null;
  superClass: string | null;
  interfaces: string[];
  annotations: string[];
  methods: number;
  fields: number;
}

export interface ScanMethod {
  id: string;
  name: string;
  qualifiedName: string;
  /** METHOD, CONSTRUCTOR or FUNCTION */
  kind: string | null;
  modifiers: string[];
  returnType: string | null;
  parameterNames: string[];
  parameterTypes: string[];
  annotations: string[];
  /** The qualified name of its class; null for a function */
  className: string | null;
  module: string;
  file: string | null;
}

/**
 * What each kind lists.
 */
export type ScanCodeItems = {
  modules: ScanModule;
  packages: ScanPackage;
  files: ScanFile;
  classes: ScanClass;
  methods: ScanMethod;
};
