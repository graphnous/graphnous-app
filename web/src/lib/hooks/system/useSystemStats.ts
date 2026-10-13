"use client";

import type { SystemStats } from "@/types";
import { useFetch } from "@/lib/hooks/useFetch";

/**
 * The stats of the system, added up from the latest scan that has any of
 * each of its projects: all zero before any of them completed a scan.
 */
export function useSystemStats(systemId: string | null | undefined) {
    const { data, loading, error, refresh } = useFetch<SystemStats>(
        systemId ? `/api/systems/${encodeURIComponent(systemId)}/stats` : null,
        "Failed to load the system's stats",
    );

    return {
        stats: data,
        loading,
        error,
        refresh,
    };
}
