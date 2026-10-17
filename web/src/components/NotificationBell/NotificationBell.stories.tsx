import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect } from "storybook/test";

import { NotificationBell } from "./NotificationBell";

const meta = {
  title: "Notifications/NotificationBell",
  component: NotificationBell,
  args: { unread: 2 },
} satisfies Meta<typeof NotificationBell>;

export default meta;
type Story = StoryObj<typeof meta>;

export const WithUnread: Story = {
  play: async ({ canvas }) => {
    const bell = canvas.getByRole("link", { name: "Notifications, 2 unread" });

    await expect(bell).toHaveAttribute("href", "/notifications");
    await expect(canvas.getByTestId("unread-dot")).toBeInTheDocument();
  },
};

export const AllRead: Story = {
  args: { unread: 0 },
  play: async ({ canvas }) => {
    await expect(canvas.getByRole("link", { name: "Notifications" })).toBeInTheDocument();
    await expect(canvas.queryByTestId("unread-dot")).not.toBeInTheDocument();
  },
};
