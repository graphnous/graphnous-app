"use client";

import { useState, useCallback } from "react";
import type { Project, Scan, System } from "@/types";

export type CreateScanRequest = {
    branch?: string;
    revision?: string;
};

export function useCreateScan({ projectId }: { projectId: string }) {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const createScan = useCallback(
        async (request: CreateScanRequest): Promise<Scan> => {
            setLoading(true);
            setError(null);

            try {
                const response = await fetch(`/api/projects/${projectId}/scans`, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify(request),
                });

                if (!response.ok) {
                    throw new Error("Failed to create scan");
                }

                return await response.json();
            } catch (error) {
                const err =
                    error instanceof Error
                        ? error
                        : new Error("Failed to create scan");

                setError(err);
                throw err;
            } finally {
                setLoading(false);
            }
        },
        [],
    );

    return {
        createScan,
        loading,
        error,
    };
}
