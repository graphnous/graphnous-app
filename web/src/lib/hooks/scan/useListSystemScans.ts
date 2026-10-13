"use client";

import type { Page, Scan } from "@/types";
import { emptyPage } from "@/types/page";
import { useFetch } from "@/lib/hooks/useFetch";

/**
 * What the scans of a system can be sorted by.
 */
export type SystemScanSort = "createdAt" | "updatedAt";

export type UseListSystemScansOptions = {
    /**
     * The system whose projects' scans to list; nothing without one.
     */
    systemId: string | null | undefined;
    /**
     * From 0.
     */
    page?: number;
    /**
     * Up to 100; 20 when missing.
     */
    size?: number;
    sort?: SystemScanSort;
    /**
     * Newest first when missing.
     */
    direction?: "asc" | "desc";
};

const NO_SCANS = emptyPage<Scan>();

/**
 * A page of the scans of every project of a system, loaded again when any
 * option changes.
 */
export function useListSystemScans({
    systemId,
    page = 0,
    size = 20,
    sort = "createdAt",
    direction = "desc",
}: UseListSystemScansOptions) {
    const search = new URLSearchParams({ page: String(page), size: String(size), sort, direction });

    const { data, loading, error, refresh } = useFetch<Page<Scan>>(
        systemId ? `/api/systems/${encodeURIComponent(systemId)}/scans?${search}` : null,
        "Failed to load the system's scans",
    );

    return {
        scans: data?.content ?? NO_SCANS.content,
        page: data ?? NO_SCANS,
        loading,
        error,
        refresh,
    };
}
