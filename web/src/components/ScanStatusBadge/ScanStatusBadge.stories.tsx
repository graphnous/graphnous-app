import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect } from "storybook/test";

import type { ScanStatus } from "@/types";

import { ScanStatusBadge } from "./ScanStatusBadge";

const meta = {
  title: "Scans/ScanStatusBadge",
  component: ScanStatusBadge,
  args: { status: "COMPLETED" },
} satisfies Meta<typeof ScanStatusBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvas }) => {
    await expect(canvas.getByText("Completed")).toBeVisible();
  },
};

export const Running: Story = {
  args: { status: "RUNNING" },
  play: async ({ canvasElement }) => {
    // The dot pulses while the scan runs
    await expect(canvasElement.querySelector(".animate-ping")).not.toBeNull();
  },
};

const all: ScanStatus[] = ["PENDING", "QUEUED", "RUNNING", "COMPLETED", "FAILED"];

export const AllStatuses: Story = {
  render: () => (
    <div className="flex flex-wrap gap-2">
      {all.map((status) => (
        <ScanStatusBadge key={status} status={status} />
      ))}
    </div>
  ),
  play: async ({ canvas, canvasElement }) => {
    for (const label of ["Pending", "Queued", "Running", "Completed", "Failed"]) {
      await expect(canvas.getByText(label)).toBeVisible();
    }
    // Only the running scan pulses
    await expect(canvasElement.querySelectorAll(".animate-ping")).toHaveLength(1);
  },
};
