/**
 * How much the latest scan of a project with stats covered, counted from
 * its results.
 */
export interface ProjectStats {
  /**
   * The scan the stats were counted from; null when no scan of the project
   * has stats yet, and everything else is zero.
   */
  scanId: string | null;
  /**
   * The modules of every target.
   */
  numberOfModulesScanned: number;
  /**
   * The files by the language of their target, such as JAVA; each a path
   * from the repository's root.
   */
  languages: Record<string, string[]>;
  /**
   * The classes, each counted once per module.
   */
  numberOfClassesParsed: number;
  /**
   * The methods of those classes, and the functions declared outside any
   * class.
   */
  numberOfMethodsParsed: number;
}
