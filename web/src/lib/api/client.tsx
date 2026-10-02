"use client";

import { createContext, useContext, useMemo, type ReactNode } from "react";

import {
  BASE_PATH,
  Configuration,
  ProjectsApi,
  ScanLogsApi,
  ScansApi,
  SystemsApi,
  type Middleware,
} from "@/generated/api";

export type ApiClient = {
  /**
   * The API's base URL, such as for an event stream.
   */
  baseUrl: string;
  projects: ProjectsApi;
  scans: ScansApi;
  scanLogs: ScanLogsApi;
  systems: SystemsApi;
};

const ApiClientContext = createContext<ApiClient | null>(null);

export type ApiClientProviderProps = {
  /**
   * Overrides NEXT_PUBLIC_GRAPHNOUS_API_URL, such as in tests.
   */
  baseUrl?: string;
  /**
   * Runs before and after each request, such as to add credentials.
   */
  middleware?: Middleware[];
  /**
   * Overrides fetch, such as to answer requests in stories.
   */
  fetchApi?: typeof fetch;
  /**
   * "include" sends cookies to an API on another origin.
   */
  credentials?: RequestCredentials;
  children: ReactNode;
};

/**
 * The API's base URL: NEXT_PUBLIC_GRAPHNOUS_API_URL, read when the app is
 * built, or the one in the OpenAPI spec.
 */
export const defaultBaseUrl = (process.env.NEXT_PUBLIC_GRAPHNOUS_API_URL ?? BASE_PATH).replace(/\/+$/, "");

/**
 * One configured client for the API, generated from its OpenAPI spec, for
 * the components below it; they get it with useApi.
 */
export function ApiClientProvider({
  baseUrl = defaultBaseUrl,
  middleware,
  fetchApi,
  credentials,
  children,
}: ApiClientProviderProps) {
  const client = useMemo(() => {
    const configuration = new Configuration({
      basePath: baseUrl,
      middleware,
      fetchApi,
      credentials,
    });

    return {
      baseUrl,
      projects: new ProjectsApi(configuration),
      scans: new ScansApi(configuration),
      scanLogs: new ScanLogsApi(configuration),
      systems: new SystemsApi(configuration),
    };
  }, [baseUrl, middleware, fetchApi, credentials]);

  return <ApiClientContext value={client}>{children}</ApiClientContext>;
}

/**
 * The API client of the ApiClientProvider around the component.
 */
export function useApi(): ApiClient {
  const client = useContext(ApiClientContext);

  if (!client) {
    throw new Error("useApi needs an ApiClientProvider around the component");
  }

  return client;
}
