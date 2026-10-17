"use client";

import { useEventStream } from "@/lib/hooks/useEventStream";
import type { NotificationEvent } from "@/types";

/**
 * The names of the events a notification stream sends, one for each type.
 */
const EVENTS = ["created", "updated", "deleted", "all_read"] as const;

/**
 * Calls onEvent with what happens to the system's notifications from now
 * on: of everyone, and of what the user reads. No system, no stream.
 */
export function useNotificationEvents(
    systemId: string | null | undefined,
    onEvent: (event: NotificationEvent) => void,
) {
    return useEventStream<NotificationEvent>(
        systemId ? `/api/systems/${encodeURIComponent(systemId)}/notifications/stream` : null,
        { event: EVENTS, onEvent },
    );
}
