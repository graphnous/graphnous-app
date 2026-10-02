import type { ScanStep } from "./ScanStep";

/**
 * How a scan is executing: its steps.
 */
export interface ScanExecution {
  scanId: string;
  /**
   * Every step of the scan, in the order they run.
   */
  steps: ScanStep[];
}
