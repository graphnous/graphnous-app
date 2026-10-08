"use client";

import { emptyPage } from "@/types/page";
import type { ScanLog } from "@/types";
import { ScanLogPage } from "@/generated/api";
import { useFetch } from "@/lib/hooks/useFetch";

const NO_LOGS = emptyPage<ScanLog>();

export function useGetScanLogs(scanId: string) {
    const { data, loading, error, refresh } = useFetch<ScanLogPage>(
        scanId ? `/api/scans/${scanId}/logs` : null,
        "Failed to load scan logs",
    );

    return {
        scanLogs: data?.content ?? NO_LOGS.content,
        page: data ?? NO_LOGS,
        loading,
        error,
        refresh,
    };
}
