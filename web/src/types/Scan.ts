export type ScanStatus = "PENDING" | "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED";

/**
 * A scan of a project at a branch and revision.
 */
export interface Scan {
  id: string;
  projectId: string;
  branch: string;
  /**
   * The commit the scan is of, as its full hash once the scan has checked
   * it out; null until then. For an uploaded scan, the revision it was
   * uploaded with.
   */
  revision?: string | null;
  /**
   * The revision the scan was asked for, such as a tag or a short commit
   * hash, as it was given; null when it was asked for the tip of its branch.
   */
  requestedRevision?: string | null;
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
