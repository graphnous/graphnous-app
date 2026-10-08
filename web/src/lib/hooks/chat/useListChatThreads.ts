"use client";

import type { ChatThread, Page } from "@/types";
import { useFetch } from "@/lib/hooks/useFetch";

const NO_THREADS: ChatThread[] = [];

/**
 * The caller's conversations about the system, the last one talked in
 * first.
 */
export function useListChatThreads(systemId: string | null | undefined) {
    const { data, loading, error, refresh } = useFetch<Page<ChatThread>>(
        systemId ? `/api/systems/${systemId}/chat/threads?size=100` : null,
        "Failed to load conversations",
    );

    return {
        // Only ever this system's threads, not still the last one's
        threads: data?.content ?? NO_THREADS,
        loaded: data !== null,
        loading,
        error,
        refresh,
    };
}
