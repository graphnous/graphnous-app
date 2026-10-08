"use client";

import { Page, type Scan } from "@/types";
import { emptyPage } from "@/types/page";
import { useFetch } from "@/lib/hooks/useFetch";

const NO_SCANS = emptyPage<Scan>();

export function useListScans(projectId: string) {
    const { data, loading, error, refresh } = useFetch<Page<Scan>>(
        projectId ? `/api/projects/${projectId}/scans` : null,
        "Failed to load scans",
    );

    return {
        scans: data?.content ?? NO_SCANS.content,
        page: data ?? NO_SCANS,
        loading,
        error,
        refresh,
    };
}
