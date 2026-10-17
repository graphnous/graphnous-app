import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, fn, userEvent } from "storybook/test";

import { NOTIFICATIONS } from "@/components/NotificationList/notifications.fixture";

import { NotificationDetail } from "./NotificationDetail";

const meta = {
  title: "Notifications/NotificationDetail",
  component: NotificationDetail,
  args: {
    notification: NOTIFICATIONS[0],
    projectName: "Graphnous App",
    onMarkRead: fn(),
    onDelete: fn(),
  },
} satisfies Meta<typeof NotificationDetail>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Unread: Story = {
  play: async ({ args, canvas }) => {
    await expect(canvas.getByRole("heading", { name: "Scan failed" })).toBeVisible();
    await expect(canvas.getByRole("link", { name: "Graphnous App" })).toHaveAttribute("href", `/projects/${NOTIFICATIONS[0].projectId}`);
    await expect(canvas.getByRole("link", { name: "Open the scan" })).toHaveAttribute("href", `/scans/${NOTIFICATIONS[0].scanId}`);

    await userEvent.click(canvas.getByRole("button", { name: "Mark as read" }));
    await expect(args.onMarkRead).toHaveBeenCalledWith(true);

    await userEvent.click(canvas.getByRole("button", { name: "Delete" }));
    await expect(args.onDelete).toHaveBeenCalled();
  },
};

export const Read: Story = {
  args: { notification: NOTIFICATIONS[2] },
  play: async ({ args, canvas }) => {
    await userEvent.click(canvas.getByRole("button", { name: "Mark as unread" }));
    await expect(args.onMarkRead).toHaveBeenCalledWith(false);
  },
};

export const OfTheProjectOnly: Story = {
  args: { notification: NOTIFICATIONS[3] },
  play: async ({ canvas }) => {
    await expect(canvas.queryByRole("link", { name: "Open the scan" })).not.toBeInTheDocument();
  },
};

export const NoneSelected: Story = {
  args: { notification: null },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("No notification selected")).toBeVisible();
  },
};
