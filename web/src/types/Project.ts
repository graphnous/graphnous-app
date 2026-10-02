/**
 * A project: a repository, or a folder in one, within a system.
 */
export interface Project {
  id: string;
  name: string;
  description?: string | null;
  gitUrl: string;
  /**
   * The folder of the project within the repository; missing for the whole
   * repository.
   */
  path?: string | null;
  systemId: string;
  /**
   * An ISO 8601 date and time.
   */
  createdAt: string;
  /**
   * An ISO 8601 date and time.
   */
  updatedAt: string;
}
