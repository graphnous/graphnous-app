"use client";

import { useCallback, useState } from "react";

import type { ChatThread } from "@/types";

/**
 * Starts conversations with the assistant about a system.
 */
export function useCreateChatThread() {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const createThread = useCallback(
        async (systemId: string): Promise<ChatThread> => {
            setLoading(true);
            setError(null);

            try {
                const response = await fetch(`/api/systems/${systemId}/chat/threads`, {
                    method: "POST",
                });

                if (!response.ok) {
                    throw new Error("Failed to start a conversation");
                }

                return await response.json();
            } catch (error) {
                const err =
                    error instanceof Error
                        ? error
                        : new Error("Failed to start a conversation");

                setError(err);
                throw err;
            } finally {
                setLoading(false);
            }
        },
        [],
    );

    return {
        createThread,
        loading,
        error,
    };
}
