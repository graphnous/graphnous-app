import { Button, cn, EmptyState, formatRelative, Skeleton, Text } from "@graphnous/theme";
import { BellSlashIcon, ChecksIcon } from "@phosphor-icons/react";

import type { Notification } from "@/types";

export type NotificationListProps = {
  notifications: Notification[];
  selectedId?: string | null;
  loading?: boolean;
  onSelect: (notification: Notification) => void;
  /**
   * Shown while any is unread.
   */
  onMarkAllRead?: () => void;
  markingAllRead?: boolean;
  /**
   * For the times shown, as "5 minutes ago"; now when left out.
   */
  now?: Date;
};

/**
 * The user's notifications, newest first: the unread on a light primary
 * background, the selected one marked.
 */
export function NotificationList({
  notifications,
  selectedId,
  loading,
  onSelect,
  onMarkAllRead,
  markingAllRead,
  now,
}: NotificationListProps) {
  const unread = notifications.filter((notification) => !notification.read).length;

  return (
    <section aria-label="Notifications" className="flex flex-col overflow-hidden rounded-md border border-border bg-surface">
      <header className="flex items-center justify-between gap-2 border-b border-border px-4 py-3">
        <div className="flex items-baseline gap-2">
          <Text weight="semibold">Notifications</Text>
          {unread > 0 && <Text size="sm" tone="muted">{unread} unread</Text>}
        </div>
        {onMarkAllRead && unread > 0 && (
          <Button variant="ghost" size="sm" icon={ChecksIcon} loading={markingAllRead} onClick={onMarkAllRead}>
            Mark all read
          </Button>
        )}
      </header>

      {loading && notifications.length === 0 ? (
        <div className="flex flex-col gap-3 p-4">
          <Skeleton lines={2} />
          <Skeleton lines={2} />
          <Skeleton lines={2} />
        </div>
      ) : notifications.length === 0 ? (
        <EmptyState
          icon={BellSlashIcon}
          title="No notifications"
          description="Scans that start, complete or fail show here."
          className="py-12"
        />
      ) : (
        <ul className="divide-y divide-border overflow-y-auto">
          {notifications.map((notification) => {
            const selected = notification.id === selectedId;

            return (
              <li key={notification.id}>
                <button
                  type="button"
                  onClick={() => onSelect(notification)}
                  aria-current={selected ? "true" : undefined}
                  className={cn(
                    "flex w-full flex-col gap-1 border-l-2 px-4 py-3 text-left transition-colors",
                    "focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary",
                    notification.read ? "hover:bg-surface-muted" : "bg-primary-subtle/50 hover:bg-primary-subtle/70",
                    selected ? "border-primary" : "border-transparent",
                  )}
                >
                  <span className="flex items-center justify-between gap-2">
                    <span className="flex min-w-0 items-center gap-2">
                      {!notification.read && (
                        <span aria-label="Unread" className="size-2 shrink-0 rounded-full bg-primary" />
                      )}
                      <Text size="sm" weight={notification.read ? "medium" : "semibold"} className="truncate">
                        {notification.title ?? "Notification"}
                      </Text>
                    </span>
                    <Text size="xs" tone="muted" className="shrink-0">
                      {formatRelative(new Date(notification.createdAt), now)}
                    </Text>
                  </span>
                  <Text size="sm" tone="muted" className="line-clamp-2">
                    {notification.content}
                  </Text>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
