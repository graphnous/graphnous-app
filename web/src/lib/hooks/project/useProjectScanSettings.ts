"use client";

import { useCallback } from "react";

import { useFetch } from "@/lib/hooks/useFetch";

type ScanSettings = {
    excludes: string[];
    languages: string[];
};

/**
 * What a project's scans leave out: the patterns of the files, such as
 * *.spec.ts; and the languages they scan, such as JAVA, none for every
 * language they find. Null until they are loaded. Nothing while not
 * enabled, such as while the dialog that shows them is closed.
 */
export function useProjectScanSettings(projectId: string | null | undefined, enabled = true) {
    const path = projectId ? `/api/projects/${encodeURIComponent(projectId)}/scan-settings` : null;

    const { data, loading, error, refresh } = useFetch<ScanSettings>(
        enabled ? path : null,
        "Failed to load what the project's scans leave out",
    );

    /**
     * Sets what the project's scans leave out and which languages they
     * scan from now on; a setting left out stays as it is. Throws with the
     * API's message for a pattern that is not well formed, or a language
     * the server has no scanner for.
     */
    const saveSettings = useCallback(
        async (settings: Partial<ScanSettings>): Promise<Partial<ScanSettings>> => {
            if (!path) {
                return settings;
            }

            const response = await fetch(path, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(settings),
            });

            const body = await response.json().catch(() => null);

            if (!response.ok) {
                throw new Error(
                    typeof body?.message === "string" && body.message
                        ? body.message
                        : "Failed to save the project's scan settings",
                );
            }

            return body ?? settings;
        },
        [path],
    );

    return {
        excludes: data?.excludes ?? null,
        languages: data?.languages ?? null,
        loading,
        error,
        refresh,
        saveSettings,
    };
}
