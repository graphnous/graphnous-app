"use client";

import { Alert, ConfirmDialog, Container, PageHeader, Stack } from "@graphnous/theme";
import { useState } from "react";

import { NotificationDetail } from "@/components/NotificationDetail/NotificationDetail";
import { NotificationList } from "@/components/NotificationList/NotificationList";
import { breadcrumbs } from "@/lib/breadcrumbs";
import { useNotificationActions } from "@/lib/hooks/notification/useNotificationActions";
import { useNotifications } from "@/lib/hooks/notification/useNotifications";
import { useSystemStore } from "@/lib/store/systemStore";
import type { Notification } from "@/types";

export default function NotificationsPage() {
    const selectedSystem = useSystemStore((state) => state.selectedSystem);

    const { notifications, loading, error, refresh } = useNotifications(selectedSystem?.id);
    const { markRead, markAllRead, deleteNotification, pending } = useNotificationActions();

    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [confirmingDelete, setConfirmingDelete] = useState(false);
    const [failure, setFailure] = useState<string | null>(null);

    // The one chosen, while it is listed; or else the newest
    const selected = notifications.find((notification) => notification.id === selectedId) ?? notifications[0] ?? null;

    const attempt = async (action: () => Promise<unknown>) => {
        setFailure(null);

        try {
            await action();
            await refresh();
        } catch (error) {
            setFailure(error instanceof Error ? error.message : String(error));
        }
    };

    // Opening one reads it
    const select = (notification: Notification) => {
        setSelectedId(notification.id);

        if (!notification.read) {
            void attempt(() => markRead(notification.id, true));
        }
    };

    return (
        <Container>
            <Stack gap={6}>
                <PageHeader
                    title="Notifications"
                    breadcrumbs={breadcrumbs(selectedSystem, { label: "Notifications" })}
                    description={`What happened to the scans of ${selectedSystem?.name ?? "the system"}.`}
                />

                {(error || failure) && (
                    <Alert tone="error" title="Something went wrong" announce>
                        {failure ?? error?.message}
                    </Alert>
                )}

                <div className="grid gap-4 lg:grid-cols-3">
                    <div className="lg:col-span-1">
                        <NotificationList
                            notifications={notifications}
                            selectedId={selected?.id}
                            loading={loading}
                            onSelect={select}
                            onMarkAllRead={selectedSystem ? () => void attempt(() => markAllRead(selectedSystem.id)) : undefined}
                            markingAllRead={pending}
                        />
                    </div>
                    <div className="lg:col-span-2">
                        <NotificationDetail
                            notification={selected}
                            busy={pending}
                            onMarkRead={selected ? (read) => void attempt(() => markRead(selected.id, read)) : undefined}
                            onDelete={selected ? () => setConfirmingDelete(true) : undefined}
                        />
                    </div>
                </div>
            </Stack>

            <ConfirmDialog
                open={confirmingDelete}
                onClose={() => setConfirmingDelete(false)}
                onConfirm={() => {
                    if (!selected) {
                        return;
                    }

                    void attempt(async () => {
                        await deleteNotification(selected.id);
                        setSelectedId(null);
                    }).finally(() => setConfirmingDelete(false));
                }}
                title="Delete this notification?"
                description="It is gone for everyone who can read the system's notifications."
                confirmLabel="Delete"
                danger
                confirming={pending}
            />
        </Container>
    );
}
