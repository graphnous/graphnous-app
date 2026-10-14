"use client";

import { useCallback, useState } from "react";

import type { Scan } from "@/types";

/**
 * Enhances completed scans again, such as with an enhancer installed or
 * updated since. Enhancing continues in the background, as the scan's step
 * that enhances its results. A refusal is thrown and kept as the error,
 * with the API's message, such as that the scan kept no results to enhance.
 */
export function useEnhanceScan() {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const enhanceScan = useCallback(
        /**
         * @param enhancers the names of the enhancers to run; all of them
         *                  when missing, none when empty
         * @param restore   whether to store the results the scan kept again
         *                  first, as the server stores them now
         */
        async (scanId: string, enhancers?: string[], restore?: boolean): Promise<Scan> => {
            setLoading(true);
            setError(null);

            try {
                const response = await fetch(`/api/scans/${encodeURIComponent(scanId)}/enhancements`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        ...(enhancers ? { enhancers } : {}),
                        ...(restore ? { restore: true } : {}),
                    }),
                });

                if (!response.ok) {
                    throw new Error(await message(response));
                }

                return await response.json();
            } catch (error) {
                const err =
                    error instanceof Error
                        ? error
                        : new Error("Failed to enhance scan");

                setError(err);
                throw err;
            } finally {
                setLoading(false);
            }
        },
        [],
    );

    const reset = useCallback(() => setError(null), []);

    return {
        enhanceScan,
        loading,
        error,
        reset,
    };
}

/**
 * The API's message from an error response, or a general one.
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

    return "Failed to enhance scan";
}
