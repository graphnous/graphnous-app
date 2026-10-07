"use client";

import { useCallback, useEffect, useState } from "react";

import { Page, type Scan } from "@/types";
import { emptyPage } from "@/types/page";

export function useListScans(projectId: string) {
    const [scans, setScans] = useState<Scan[]>([]);
    const [page, setPage] = useState<Page<Scan>>(emptyPage());

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const load = useCallback(async () => {
        if (!projectId) {
            setScans([]);
            return;
        }

        try {
            setLoading(true);
            setError(null);

            const response = await fetch(
                `/api/projects/${projectId}/scans`,
            );

            if (!response.ok) {
                throw new Error("Failed to load scans");
            }

            const p = await response.json();

            setPage(p);
            setScans(p.content);
        } catch (error) {
            setError(
                error instanceof Error
                    ? error
                    : new Error("Failed to load scans"),
            );
        } finally {
            setLoading(false);
        }
    }, [projectId]);

    useEffect(() => {
        load();
    }, [load]);

    return {
        scans,
        page,
        loading,
        error,
        refresh: load,
    };
}