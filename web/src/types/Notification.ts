/**
 * A message about a system, one of its projects, or one of their scans,
 * such as a scan that failed.
 */
export interface Notification {
  id: string;
  systemId: string;
  /**
   * The project it is about; null when it is about the system as a whole.
   */
  projectId?: string | null;
  /**
   * The scan it is about; null when it is about none.
   */
  scanId?: string | null;
  /**
   * A short summary, such as "Scan failed"; null when it has none.
   */
  title?: string | null;
  content: string;
  /**
   * Whether the user read it: each user reads for themselves.
   */
  read: boolean;
  /**
   * An ISO 8601 date and time.
   */
  createdAt: string;
}

/**
 * What happened to a notification of a system, as its stream sends it:
 * of one notification, or of all of them, marked read.
 */
export type NotificationEventType = "CREATED" | "UPDATED" | "DELETED" | "ALL_READ";

export interface NotificationEvent {
  type: NotificationEventType;
  systemId: string;
  /**
   * Left out for ALL_READ.
   */
  notificationId?: string;
  /**
   * The notification as it is now, for CREATED and UPDATED.
   */
  notification?: Notification;
}
