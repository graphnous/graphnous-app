import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, fn, userEvent } from "storybook/test";

import { NotificationList } from "./NotificationList";
import { NOTIFICATIONS, NOW } from "./notifications.fixture";

const meta = {
  title: "Notifications/NotificationList",
  component: NotificationList,
  args: {
    notifications: NOTIFICATIONS,
    selectedId: "n2",
    now: NOW,
    onSelect: fn(),
    onMarkAllRead: fn(),
  },
  decorators: [(Story) => <div className="max-w-sm"><Story /></div>],
} satisfies Meta<typeof NotificationList>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ args, canvas }) => {
    await expect(canvas.getByText("2 unread")).toBeVisible();
    await expect(canvas.getAllByLabelText("Unread")).toHaveLength(2);
    await expect(canvas.getByText("4 minutes ago")).toBeVisible();

    // The selected one is marked
    await expect(canvas.getByRole("button", { name: /Scan completed/ })).toHaveAttribute("aria-current", "true");

    await userEvent.click(canvas.getByRole("button", { name: /Scan failed/ }));
    await expect(args.onSelect).toHaveBeenCalledWith(NOTIFICATIONS[0]);

    await userEvent.click(canvas.getByRole("button", { name: "Mark all read" }));
    await expect(args.onMarkAllRead).toHaveBeenCalled();
  },
};

export const AllRead: Story = {
  args: { notifications: NOTIFICATIONS.map((notification) => ({ ...notification, read: true })) },
  play: async ({ canvas }) => {
    await expect(canvas.queryByRole("button", { name: "Mark all read" })).not.toBeInTheDocument();
    await expect(canvas.queryByLabelText("Unread")).not.toBeInTheDocument();
  },
};

export const Empty: Story = {
  args: { notifications: [] },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("No notifications")).toBeVisible();
  },
};

export const Loading: Story = {
  args: { notifications: [], loading: true },
};
