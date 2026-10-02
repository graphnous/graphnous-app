import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect } from "storybook/test";

import type { ScanLogLevel } from "@/types";

import { ScanLogLevelBadge } from "./ScanLogLevelBadge";

const meta = {
  title: "Scan logs/ScanLogLevelBadge",
  component: ScanLogLevelBadge,
  args: { level: "INFO" },
} satisfies Meta<typeof ScanLogLevelBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvas }) => {
    await expect(canvas.getByText("INFO")).toBeVisible();
  },
};

const all: ScanLogLevel[] = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"];

export const AllLevels: Story = {
  render: () => (
    <div className="flex flex-wrap gap-2">
      {all.map((level) => (
        <ScanLogLevelBadge key={level} level={level} />
      ))}
    </div>
  ),
  play: async ({ canvas }) => {
    for (const level of all) {
      await expect(canvas.getByText(level)).toBeVisible();
    }
  },
};
