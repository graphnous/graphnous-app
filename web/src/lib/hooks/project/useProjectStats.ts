"use client";

import type { ProjectStats } from "@/types";
import { useFetch } from "@/lib/hooks/useFetch";

/**
 * The stats of the project's latest scan that has any: all zero, with no
 * scan, when none has, such as before its first scan completed.
 */
export function useProjectStats(projectId: string | null | undefined) {
    const { data, loading, error, refresh } = useFetch<ProjectStats>(
        projectId ? `/api/projects/${encodeURIComponent(projectId)}/stats` : null,
        "Failed to load the project's stats",
    );

    return {
        stats: data,
        loading,
        error,
        refresh,
    };
}
