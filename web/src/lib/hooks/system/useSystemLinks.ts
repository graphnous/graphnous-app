"use client";

import type { SystemLink, SystemLinks } from "@/types";
import { useFetch } from "@/lib/hooks/useFetch";

export type UseSystemLinksOptions = {
    /**
     * The system whose projects to link; nothing without one.
     */
    systemId: string | null | undefined;
    /**
     * The scans to link, such as the ones a graph shows; the latest
     * completed scan of each project when missing, and nothing when empty.
     */
    scanIds?: string[];
};

const NO_LINKS: SystemLink[] = [];

/**
 * How the projects of a system are linked over HTTP: which HTTP calls of
 * their scans request which endpoints. Loaded again when the scans change.
 */
export function useSystemLinks({ systemId, scanIds }: UseSystemLinksOptions) {
    // In one order, so the same scans are the same request
    const scans = scanIds ? [...scanIds].sort().join(",") : null;
    const search = scans ? `?${new URLSearchParams({ scans })}` : "";

    const { data, loading, error, refresh } = useFetch<SystemLinks>(
        systemId && scans !== "" ? `/api/systems/${encodeURIComponent(systemId)}/links${search}` : null,
        "Failed to load the links between the projects",
    );

    return {
        links: data?.links ?? NO_LINKS,
        calls: data?.calls ?? 0,
        endpoints: data?.endpoints ?? 0,
        loading,
        error,
        refresh,
    };
}
