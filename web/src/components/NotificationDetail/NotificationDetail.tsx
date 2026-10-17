import { Button, EmptyState, Heading, Link, Text, Timestamp } from "@graphnous/theme";
import { BellIcon, EnvelopeOpenIcon, EnvelopeSimpleIcon, TrashIcon } from "@phosphor-icons/react";

import type { Notification } from "@/types";

export type NotificationDetailProps = {
  /**
   * None while no notification is selected.
   */
  notification?: Notification | null;
  /**
   * The names of the notification's project, when known.
   */
  projectName?: string;
  onMarkRead?: (read: boolean) => void;
  onDelete?: () => void;
  busy?: boolean;
};

/**
 * The selected notification in full, with what it is about and what can
 * be done with it.
 */
export function NotificationDetail({ notification, projectName, onMarkRead, onDelete, busy }: NotificationDetailProps) {
  if (!notification) {
    return (
      <section aria-label="Notification" className="rounded-md border border-border bg-surface">
        <EmptyState
          icon={BellIcon}
          title="No notification selected"
          description="Choose a notification in the list to read it."
          className="py-16"
        />
      </section>
    );
  }

  return (
    <section aria-label="Notification" className="flex flex-col gap-5 rounded-md border border-border bg-surface p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <Heading level={2} size="md">
            {notification.title ?? "Notification"}
          </Heading>
          <Text size="sm" tone="muted">
            <Timestamp value={notification.createdAt} />
          </Text>
        </div>

        <div className="flex items-center gap-2">
          {onMarkRead && (
            <Button
              variant="secondary"
              size="sm"
              icon={notification.read ? EnvelopeSimpleIcon : EnvelopeOpenIcon}
              disabled={busy}
              onClick={() => onMarkRead(!notification.read)}
            >
              {notification.read ? "Mark as unread" : "Mark as read"}
            </Button>
          )}
          {onDelete && (
            <Button variant="ghost" size="sm" icon={TrashIcon} disabled={busy} onClick={onDelete}>
              Delete
            </Button>
          )}
        </div>
      </header>

      <Text className="whitespace-pre-wrap">{notification.content}</Text>

      {(notification.projectId || notification.scanId) && (
        <dl className="flex flex-wrap gap-x-8 gap-y-2 border-t border-border pt-4 text-sm">
          {notification.projectId && (
            <div className="flex gap-2">
              <dt className="text-foreground-muted">Project</dt>
              <dd>
                <Link href={`/projects/${encodeURIComponent(notification.projectId)}`}>
                  {projectName ?? "Open the project"}
                </Link>
              </dd>
            </div>
          )}
          {notification.scanId && (
            <div className="flex gap-2">
              <dt className="text-foreground-muted">Scan</dt>
              <dd>
                <Link href={`/scans/${encodeURIComponent(notification.scanId)}`}>Open the scan</Link>
              </dd>
            </div>
          )}
        </dl>
      )}
    </section>
  );
}
