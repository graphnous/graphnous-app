"use client";

import { useFetch } from "@/lib/hooks/useFetch";
import { useNotificationEvents } from "@/lib/hooks/notification/useNotificationEvents";
import { emptyPage } from "@/types/page";
import type { Notification, Page } from "@/types";

const NONE = emptyPage<Notification>();

export type NotificationsOptions = {
    /**
     * Only those the user has not read.
     */
    unread?: boolean;
    /**
     * Zero-based.
     */
    page?: number;
    size?: number;
};

/**
 * A page of the system's notifications, newest first, each read or not as
 * the user read it; kept current by the system's notification stream.
 */
export function useNotifications(
    systemId: string | null | undefined,
    { unread = false, page = 0, size = 50 }: NotificationsOptions = {},
) {
    const search = new URLSearchParams({ page: String(page), size: String(size) });

    if (unread) {
        search.set("unread", "true");
    }

    const { data, loading, error, refresh } = useFetch<Page<Notification>>(
        systemId ? `/api/systems/${encodeURIComponent(systemId)}/notifications?${search}` : null,
        "Failed to load the notifications",
    );

    useNotificationEvents(systemId, () => {
        void refresh();
    });

    return {
        notifications: data?.content ?? NONE.content,
        page: data ?? NONE,
        loading,
        error,
        refresh,
    };
}
