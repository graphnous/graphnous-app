"use client";

import { useCallback, useEffect, useState } from "react";

import type { ScanLog } from "@/types";
import { emptyPage } from "@/types/page";
import { ScanLogPage } from "@/generated/api";

export function useGetScanLogs(scanId: string) {
    const [scanLogs, setScanLogs] = useState<ScanLog[]>([]);
    const [page, setPage] = useState<ScanLogPage>(emptyPage());

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const load = useCallback(async () => {
        if (!scanId) {
            setScanLogs([]);
            setPage(emptyPage())
            return;
        }

        try {
            setLoading(true);
            setError(null);

            const response = await fetch(
                `/api/scans/${scanId}/logs`,
            );

            if (!response.ok) {
                throw new Error("Failed to load scan logs");
            }

            const res = await response.json();

            setPage(res);
            setScanLogs(res.content);
        } catch (error) {
            setError(
                error instanceof Error
                    ? error
                    : new Error("Failed to load scan logs"),
            );
        } finally {
            setLoading(false);
        }
    }, [scanId]);

    useEffect(() => {
        load();
    }, [load]);

    return {
        scanLogs,
        page,
        loading,
        error,
        refresh: load,
    };
}