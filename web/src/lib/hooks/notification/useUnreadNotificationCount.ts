"use client";

import { useFetch } from "@/lib/hooks/useFetch";
import { useNotificationEvents } from "@/lib/hooks/notification/useNotificationEvents";

/**
 * How many of the system's notifications the user has not read, kept
 * current by the system's notification stream; 0 until it has loaded, and
 * without a system.
 */
export function useUnreadNotificationCount(systemId: string | null | undefined) {
    const { data, loading, error, refresh } = useFetch<{ count: number }>(
        systemId ? `/api/systems/${encodeURIComponent(systemId)}/notifications/unread` : null,
        "Failed to count the unread notifications",
    );

    useNotificationEvents(systemId, () => {
        void refresh();
    });

    return {
        count: systemId ? (data?.count ?? 0) : 0,
        loading,
        error,
        refresh,
    };
}
