"use client";

import { useEffect } from "react";

import type { ScanExecution, ScanStep } from "@/types";
import { useFetch } from "@/lib/hooks/useFetch";

const NO_STEPS: ScanStep[] = [];

/**
 * The steps of a scan, in the order they run; with repoll, loaded again
 * every two seconds, to follow a scan that is running.
 */
export function useGetScanSteps(
    scanId: string,
    repoll = false,
) {
    const { data, loading, error, refresh } = useFetch<ScanExecution>(
        scanId ? `/api/scans/${scanId}/steps` : null,
        "Failed to load scan steps",
    );

    useEffect(() => {
        if (!repoll) {
            return;
        }

        const interval = setInterval(refresh, 2000);

        return () => clearInterval(interval);
    }, [refresh, repoll]);

    return {
        steps: data?.steps ?? NO_STEPS,
        loading,
        error,
        refresh,
    };
}
