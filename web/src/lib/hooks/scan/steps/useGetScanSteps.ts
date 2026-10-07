"use client";

import { useCallback, useEffect, useState } from "react";

import type { ScanExecution, ScanStep } from "@/types";

/**
 * The steps of a scan, in the order they run; with repoll, loaded again
 * every two seconds, to follow a scan that is running.
 */
export function useGetScanSteps(
    scanId: string,
    repoll = false,
) {
    const [steps, setSteps] = useState<ScanStep[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const load = useCallback(async () => {
        if (!scanId) {
            setSteps([]);
            return;
        }

        try {
            setLoading(true);
            setError(null);

            const response = await fetch(
                `/api/scans/${scanId}/steps`,
            );

            if (!response.ok) {
                throw new Error("Failed to load scan steps");
            }

            const res: ScanExecution = await response.json();

            setSteps(res.steps);
        } catch (error) {
            setError(
                error instanceof Error
                    ? error
                    : new Error("Failed to load scan steps"),
            );
        } finally {
            setLoading(false);
        }
    }, [scanId]);

    useEffect(() => {
        load();

        if (!repoll) {
            return;
        }

        const interval = setInterval(load, 2000);

        return () => clearInterval(interval);
    }, [load, repoll]);

    return {
        steps,
        loading,
        error,
        refresh: load,
    };
}
