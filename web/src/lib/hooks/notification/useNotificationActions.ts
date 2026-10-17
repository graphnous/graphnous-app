"use client";

import { useCallback, useState } from "react";

import type { Notification } from "@/types";

async function failure(response: Response, fallback: string): Promise<Error> {
    const body = await response.json().catch(() => null);

    return new Error(typeof body?.message === "string" && body.message ? body.message : fallback);
}

/**
 * Marking the user's notifications read or unread, all at once, and
 * deleting one; each throws with the API's message when it fails. The
 * lists and counts of the notification hooks follow by their stream.
 */
export function useNotificationActions() {
    const [pending, setPending] = useState(0);

    const run = useCallback(async <T,>(action: () => Promise<T>): Promise<T> => {
        setPending((count) => count + 1);

        try {
            return await action();
        } finally {
            setPending((count) => count - 1);
        }
    }, []);

    const markRead = useCallback(
        (notificationId: string, read: boolean) => run(async (): Promise<Notification> => {
            const response = await fetch(`/api/notifications/${encodeURIComponent(notificationId)}`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ read }),
            });

            if (!response.ok) {
                throw await failure(response, "Failed to mark the notification");
            }

            return response.json();
        }),
        [run],
    );

    /**
     * @returns how many were unread
     */
    const markAllRead = useCallback(
        (systemId: string) => run(async (): Promise<number> => {
            const response = await fetch(`/api/systems/${encodeURIComponent(systemId)}/notifications/read`, { method: "POST" });

            if (!response.ok) {
                throw await failure(response, "Failed to mark the notifications read");
            }

            return (await response.json()).count;
        }),
        [run],
    );

    const deleteNotification = useCallback(
        (notificationId: string) => run(async (): Promise<void> => {
            const response = await fetch(`/api/notifications/${encodeURIComponent(notificationId)}`, { method: "DELETE" });

            if (!response.ok) {
                throw await failure(response, "Failed to delete the notification");
            }
        }),
        [run],
    );

    return { markRead, markAllRead, deleteNotification, pending: pending > 0 };
}
