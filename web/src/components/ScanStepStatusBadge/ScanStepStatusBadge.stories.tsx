import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect } from "storybook/test";

import type { ScanStepStatus } from "@/types";

import { ScanStepStatusBadge } from "./ScanStepStatusBadge";

const meta = {
  title: "Scan execution/ScanStepStatusBadge",
  component: ScanStepStatusBadge,
  args: { status: "COMPLETED" },
} satisfies Meta<typeof ScanStepStatusBadge>;

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
    // The dot pulses while the step runs
    await expect(canvasElement.querySelector(".animate-ping")).not.toBeNull();
  },
};

export const Skipped: Story = {
  args: { status: "SKIPPED" },
  play: async ({ canvas, canvasElement }) => {
    await expect(canvas.getByText("Skipped")).toBeVisible();
    // A dash, not the dot of a step that is still to run
    await expect(canvasElement.querySelector("svg")).not.toBeNull();
  },
};

const all: ScanStepStatus[] = ["PENDING", "RUNNING", "COMPLETED", "FAILED", "SKIPPED"];

export const AllStatuses: Story = {
  render: () => (
    <div className="flex flex-wrap gap-2">
      {all.map((status) => (
        <ScanStepStatusBadge key={status} status={status} />
      ))}
    </div>
  ),
  play: async ({ canvas, canvasElement }) => {
    for (const label of ["Pending", "Running", "Completed", "Failed", "Skipped"]) {
      await expect(canvas.getByText(label)).toBeVisible();
    }
    await expect(canvasElement.querySelectorAll(".animate-ping")).toHaveLength(1);
  },
};
