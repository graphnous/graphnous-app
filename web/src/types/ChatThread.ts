/**
 * A conversation with the assistant about a system.
 */
export interface ChatThread {
  id: string;
  systemId: string;
  /**
   * The first question, shortened; null until one was asked.
   */
  title: string | null;
  /**
   * An ISO 8601 date and time.
   */
  createdAt: string;
  /**
   * When it was last talked in, as an ISO 8601 date and time.
   */
  updatedAt: string;
}
