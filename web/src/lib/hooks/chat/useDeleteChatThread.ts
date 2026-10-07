"use client";

import { useCallback, useState } from "react";

/**
 * Deletes conversations with the assistant.
 */
export function useDeleteChatThread() {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const deleteThread = useCallback(
        async (threadId: string): Promise<void> => {
            setLoading(true);
            setError(null);

            try {
                const response = await fetch(`/api/chat/threads/${threadId}`, {
                    method: "DELETE",
                });

                if (!response.ok) {
                    throw new Error("Failed to delete the conversation");
                }
            } catch (error) {
                const err =
                    error instanceof Error
                        ? error
                        : new Error("Failed to delete the conversation");

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
        deleteThread,
        loading,
        error,
        reset,
    };
}
