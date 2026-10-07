"use client";

import { useCallback, useState } from "react";

/**
 * Deletes scans. A failed delete is thrown and kept as the error, with the
 * API's message when it gave one, such as why a running scan cannot be
 * deleted.
 */
export function useDeleteScan() {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const deleteScan = useCallback(
        async (scanId: string): Promise<void> => {
            setLoading(true);
            setError(null);

            try {
                const response = await fetch(`/api/scans/${scanId}`, {
                    method: "DELETE",
                });

                if (!response.ok) {
                    throw new Error(await message(response));
                }
            } catch (error) {
                const err =
                    error instanceof Error
                        ? error
                        : new Error("Failed to delete scan");

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
        deleteScan,
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

    return "Failed to delete scan";
}
