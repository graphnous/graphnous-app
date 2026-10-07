"use client";

import { useCallback, useEffect, useState } from "react";

import type { ChatThread, Page } from "@/types";

/**
 * The caller's conversations about the system, the last one talked in
 * first.
 */
export function useListChatThreads(systemId: string | null | undefined) {
    const [threads, setThreads] = useState<ChatThread[]>([]);

    // The system whose threads were loaded last
    const [loadedFor, setLoadedFor] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const load = useCallback(async () => {
        if (!systemId) {
            return;
        }

        try {
            setLoading(true);
            setError(null);

            const response = await fetch(`/api/systems/${systemId}/chat/threads?size=100`);

            if (!response.ok) {
                throw new Error("Failed to load conversations");
            }

            const page: Page<ChatThread> = await response.json();

            setThreads(page.content);
            setLoadedFor(systemId);
        } catch (error) {
            setError(
                error instanceof Error
                    ? error
                    : new Error("Failed to load conversations"),
            );
        } finally {
            setLoading(false);
        }
    }, [systemId]);

    useEffect(() => {
        load();
    }, [load]);

    // Whether the threads are this system's, not still the last one's
    const loaded = systemId != null && loadedFor === systemId;

    return {
        threads: loaded ? threads : [],
        loaded,
        loading,
        error,
        refresh: load,
    };
}
