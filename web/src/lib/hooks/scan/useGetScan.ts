"use client";

import { useState, useCallback, useEffect } from "react";
import type { Scan } from "@/types";


export function useGetScan({ scanId }: { scanId: string }) {
    const [scan, setScan] = useState<Scan | null>(null);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const load = useCallback(async () => {
        if (!scanId) {
            setScan(null);
            return;
        }

        try {
            setLoading(true);
            setError(null);

            const response = await fetch(
                `/api/scans/${scanId}`,
            );

            if (!response.ok) {
                throw new Error("Failed to load scan");
            }

            const res = await response.json();

            setScan(res);
        } catch (error) {
            setError(
                error instanceof Error
                    ? error
                    : new Error("Failed to load scan"),
            );
        } finally {
            setLoading(false);
        }
    }, [scanId]);

    useEffect(() => {
        load();
    }, [load]);

    return {
        scan,
        loading,
        error,
        refresh: load,
    };
}
