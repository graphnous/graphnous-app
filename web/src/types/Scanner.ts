/**
 * A scanner the server runs: one per language it can scan.
 */
export interface Scanner {
  /**
   * Such as JAVA; what a project or scan names to scan only some languages.
   */
  language: string;
}
