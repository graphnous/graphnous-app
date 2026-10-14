"use client";

import { useCallback } from "react";

import { useFetch } from "@/lib/hooks/useFetch";

type ScanSettings = {
    excludes: string[];
};

/**
 * What a project's scans leave out: the patterns of the files, such as
 * *.spec.ts; null until they are loaded. Nothing while not enabled, such as
 * while the dialog that shows them is closed.
 */
export function useProjectScanSettings(projectId: string | null | undefined, enabled = true) {
    const path = projectId ? `/api/projects/${encodeURIComponent(projectId)}/scan-settings` : null;

    const { data, loading, error, refresh } = useFetch<ScanSettings>(
        enabled ? path : null,
        "Failed to load what the project's scans leave out",
    );

    /**
     * Sets what the project's scans leave out from now on. Throws with the
     * API's message for a pattern that is not well formed.
     */
    const saveExcludes = useCallback(
        async (excludes: string[]): Promise<string[]> => {
            if (!path) {
                return excludes;
            }

            const response = await fetch(path, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ excludes }),
            });

            const body = await response.json().catch(() => null);

            if (!response.ok) {
                throw new Error(
                    typeof body?.message === "string" && body.message
                        ? body.message
                        : "Failed to save what the project's scans leave out",
                );
            }

            return body?.excludes ?? excludes;
        },
        [path],
    );

    return {
        excludes: data?.excludes ?? null,
        loading,
        error,
        refresh,
        saveExcludes,
    };
}
