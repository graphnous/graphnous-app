"use client";

import type { ScanUsage, ScanUsages } from "@/types";
import { useFetch } from "@/lib/hooks/useFetch";

export type UseScanUsagesOptions = {
    scanId: string | null | undefined;
    /**
     * The id of the class, method or field to find the usages of, or of
     * what an enhancer added to one; nothing without one.
     */
    nodeId: string | null | undefined;
    /**
     * 1, the default, for what uses the node itself; up to 3 to also
     * follow what calls that.
     */
    depth?: number;
};

const NO_USAGES: ScanUsage[] = [];

/**
 * What uses a node of a scan, by file and line: what a change to it
 * affects. Loaded again when the node or depth change.
 */
export function useScanUsages({ scanId, nodeId, depth = 1 }: UseScanUsagesOptions) {
    const search = new URLSearchParams({ node: nodeId ?? "", depth: String(depth) });

    const { data, loading, error, refresh } = useFetch<ScanUsages>(
        scanId && nodeId ? `/api/scans/${encodeURIComponent(scanId)}/usages?${search}` : null,
        "Failed to load the usages",
    );

    return {
        subject: data?.subject ?? null,
        usages: data?.usages ?? NO_USAGES,
        truncated: data?.truncated ?? false,
        loading,
        error,
        refresh,
    };
}
