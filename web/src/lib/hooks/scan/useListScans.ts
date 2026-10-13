"use client";

import { Page, type Scan } from "@/types";
import { emptyPage } from "@/types/page";
import { useFetch } from "@/lib/hooks/useFetch";

const NO_SCANS = emptyPage<Scan>();

/**
 * As many scans as the API gives at once.
 */
const NEWEST = 100;

/**
 * The scans of a project, the newest first: its latest scans are what its
 * graph, history and dependencies are of. At most the newest 100, which is
 * what the page counts; totalElements says how many there are.
 */
export function useListScans(projectId: string) {
    const search = new URLSearchParams({ size: String(NEWEST), sort: "createdAt", direction: "desc" });

    const { data, loading, error, refresh } = useFetch<Page<Scan>>(
        projectId ? `/api/projects/${projectId}/scans?${search}` : null,
        "Failed to load scans",
    );

    return {
        scans: data?.content ?? NO_SCANS.content,
        page: data ?? NO_SCANS,
        loading,
        error,
        refresh,
    };
}
