/**
 * A system: a group of projects, such as the repositories of one product.
 */
export interface System {
  id: string;
  name: string;
  description?: string | null;
  /**
   * An ISO 8601 date and time.
   */
  createdAt: string;
  /**
   * An ISO 8601 date and time.
   */
  updatedAt: string;
}
