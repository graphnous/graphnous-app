/**
 * How much the latest scans of a system's projects covered together: the
 * stats of each project's latest scan that has any, added up.
 */
export interface SystemStats {
  systemId: string;
  /**
   * The projects with a scan that has stats.
   */
  numberOfProjectsScanned: number;
  /**
   * The modules of those projects.
   */
  numberOfModulesScanned: number;
  /**
   * The files of those projects by language, such as JAVA; each a path
   * from the root of its project's repository.
   */
  languages: Record<string, string[]>;
  /**
   * The classes of those projects.
   */
  numberOfClassesParsed: number;
  /**
   * The methods of those projects, and the functions declared outside any
   * class.
   */
  numberOfMethodsParsed: number;
}
