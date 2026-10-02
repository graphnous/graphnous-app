export type ScanLogLevel = "TRACE" | "DEBUG" | "INFO" | "WARN" | "ERROR";

/**
 * A line of a scan's log.
 */
export interface ScanLog {
  id: string;
  scanId: string;
  /**
   * The line's place in the scan's log, from 1.
   */
  sequence: number;
  /**
   * An ISO 8601 date and time.
   */
  timestamp: string;
  level: ScanLogLevel;
  message: string;
}
