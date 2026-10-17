import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { useState } from "react";
import { expect, userEvent } from "storybook/test";

import { NotificationBell } from "@/components/NotificationBell/NotificationBell";
import { NotificationList } from "@/components/NotificationList/NotificationList";
import { NOTIFICATIONS, NOW } from "@/components/NotificationList/notifications.fixture";
import type { Notification } from "@/types";

import { NotificationDetail } from "./NotificationDetail";

/**
 * The notifications page as it lays them out: the list in a third, the
 * selected one in the rest; opening one reads it.
 */
function NotificationsLayout() {
  const [notifications, setNotifications] = useState<Notification[]>(NOTIFICATIONS);
  const [selectedId, setSelectedId] = useState("n1");

  const selected = notifications.find((notification) => notification.id === selectedId) ?? null;
  const mark = (id: string, read: boolean) =>
    setNotifications((all) => all.map((notification) => (notification.id === id ? { ...notification, read } : notification)));

  return (
    <div className="flex flex-col gap-4">
      <div className="flex justify-end border-b border-border pb-2">
        <NotificationBell unread={notifications.filter((notification) => !notification.read).length} />
      </div>
      <div className="grid gap-4 lg:grid-cols-3">
        <div className="lg:col-span-1">
          <NotificationList
            notifications={notifications}
            selectedId={selectedId}
            now={NOW}
            onSelect={(notification) => {
              setSelectedId(notification.id);
              mark(notification.id, true);
            }}
            onMarkAllRead={() => setNotifications((all) => all.map((notification) => ({ ...notification, read: true })))}
          />
        </div>
        <div className="lg:col-span-2">
          <NotificationDetail
            notification={selected}
            projectName="Graphnous App"
            onMarkRead={(read) => selected && mark(selected.id, read)}
            onDelete={() => undefined}
          />
        </div>
      </div>
    </div>
  );
}

const meta = {
  title: "Notifications/NotificationsLayout",
  component: NotificationsLayout,
  parameters: { layout: "padded" },
} satisfies Meta<typeof NotificationsLayout>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const OpeningOneReadsIt: Story = {
  play: async ({ canvas }) => {
    await userEvent.click(canvas.getByRole("button", { name: /Scan completed/ }));

    await expect(canvas.getByRole("heading", { name: "Scan completed" })).toBeVisible();
    await expect(canvas.getAllByLabelText("Unread")).toHaveLength(1);
  },
};
