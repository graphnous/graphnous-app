/**
 * An enhancer installed on the server: it adds to what a scan found, such
 * as the endpoints of a Spring application.
 */
export interface Enhancer {
  /**
   * Such as spring; unique among the installed enhancers.
   */
  name: string;
  /**
   * Such as 1.0.0.
   */
  version: string;
}
