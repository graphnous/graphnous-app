"use client";

import type { Scanner } from "@/types";
import { useFetch } from "@/lib/hooks/useFetch";

const NO_SCANNERS: Scanner[] = [];

/**
 * Every scanner the server runs, one per language it can scan; none while
 * not enabled, such as while the dialog that lists them is closed.
 */
export function useListScanners(enabled = true) {
    const { data, loading, error, refresh } = useFetch<Scanner[]>(
        enabled ? "/api/scanners" : null,
        "Failed to load the scanners",
    );

    return {
        scanners: data ?? NO_SCANNERS,
        loading,
        error,
        refresh,
    };
}
