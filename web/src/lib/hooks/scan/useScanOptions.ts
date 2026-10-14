"use client";

import { useFetch } from "@/lib/hooks/useFetch";

type ScanOptions = {
    enhancers: string[] | null;
    excludes: string[];
};

const NO_EXCLUDES: string[] = [];

/**
 * What a scan was created with besides its source: the names of the
 * enhancers chosen for it, null when none were chosen, which ran every
 * installed enhancer, and until it is loaded; and the patterns of the files
 * it left out.
 */
export function useScanOptions(scanId: string | null | undefined) {
    const { data, loading, error, refresh } = useFetch<ScanOptions>(
        scanId ? `/api/scans/${encodeURIComponent(scanId)}/options` : null,
        "Failed to load what the scan was created with",
    );

    return {
        enhancers: data?.enhancers ?? null,
        excludes: data?.excludes ?? NO_EXCLUDES,
        loading,
        error,
        refresh,
    };
}
