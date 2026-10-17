"use client";

import { useState, useCallback } from "react";
import type { Project, Scan, System } from "@/types";

export type CreateScanRequest = {
    branch?: string;
    revision?: string;
    /**
     * The names of the enhancers to run on the scan's results; every
     * installed one when missing, none when empty.
     */
    enhancers?: string[];
    /**
     * The patterns of the files to leave out of the scan, such as
     * *.spec.ts; the project's own when missing, none when empty.
     */
    excludes?: string[];
    /**
     * The languages to scan, such as JAVA; the project's own when missing,
     * every language the scan finds when empty.
     */
    languages?: string[];
    /**
     * Why the scan starts: MANUAL, as when missing, or CHAT for one the
     * user approved in the assistant's chat.
     */
    trigger?: "MANUAL" | "CHAT";
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
                    throw new Error(await message(response));
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

/**
 * The API's message from an error response, such as an enhancer that is
 * not installed, or a general one.
 */
async function message(response: Response): Promise<string> {
    try {
        const body = await response.json();

        if (typeof body?.message === "string" && body.message) {
            return body.message;
        }
    } catch {
        // Not JSON; the general message will do
    }

    return "Failed to create scan";
}
