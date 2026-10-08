"use client";

import type { ScanComparison } from "@/types";
import { useFetch } from "@/lib/hooks/useFetch";

/**
 * How the head scan's graph differs from the base scan's; nothing until
 * both are chosen, and nothing to compare a scan with itself.
 */
export function useCompareScans(base: string | null, head: string | null) {
    const { data, loading, error, refresh } = useFetch<ScanComparison>(
        base && head && base !== head
            ? `/api/scans/${encodeURIComponent(base)}/compare/${encodeURIComponent(head)}`
            : null,
        "Failed to compare the scans",
    );

    return {
        comparison: data,
        loading,
        error,
        refresh,
    };
}
