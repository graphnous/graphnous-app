export type ScanStatus = "PENDING" | "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED";

/**
 * A scan of a project at a branch and revision.
 */
export interface Scan {
  id: string;
  projectId: string;
  branch: string;
  revision: string;
  status: ScanStatus;
  /**
   * An ISO 8601 date and time.
   */
  createdAt: string;
  /**
   * An ISO 8601 date and time.
   */
  updatedAt: string;
  /**
   * When the scan started running, as an ISO 8601 date and time; null until
   * then.
   */
  startedAt?: string | null;
  /**
   * When the scan completed or failed, as an ISO 8601 date and time; null
   * while it is active.
   */
  completedAt?: string | null;
}
