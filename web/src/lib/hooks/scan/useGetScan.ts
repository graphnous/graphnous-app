"use client";

import type { Scan } from "@/types";
import { useFetch } from "@/lib/hooks/useFetch";

export function useGetScan({ scanId }: { scanId: string }) {
    const { data, loading, error, refresh } = useFetch<Scan>(
        scanId ? `/api/scans/${scanId}` : null,
        "Failed to load scan",
    );

    return {
        scan: data,
        loading,
        error,
        refresh,
    };
}
