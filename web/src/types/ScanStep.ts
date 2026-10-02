/**
 * The steps of a scan, in the order they run: checking the repository out,
 * finding the targets to scan, scanning them, storing the results in the
 * graph, and running the enhancers scoped to a target and to the scan.
 */
export type ScanStepType = "CHECKOUT" | "PLAN" | "SCAN" | "STORE" | "ENHANCE_RESULTS" | "ENHANCE_SCAN";

/**
 * SKIPPED when an earlier step failed. A failed enhance step does not fail
 * the scan, as its results are stored.
 */
export type ScanStepStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED" | "SKIPPED";

/**
 * A step of executing a scan.
 */
export interface ScanStep {
  type: ScanStepType;
  status: ScanStepStatus;
  /**
   * An ISO 8601 date and time; null until the step starts.
   */
  startedAt?: string | null;
  /**
   * An ISO 8601 date and time; null until the step finishes.
   */
  finishedAt?: string | null;
  /**
   * Why the step failed; null otherwise.
   */
  error?: string | null;
}
