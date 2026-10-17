import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect } from "storybook/test";

import type { ScanTrigger } from "@/types";

import { ScanTriggerBadge } from "./ScanTriggerBadge";

const meta = {
  title: "Scans/ScanTriggerBadge",
  component: ScanTriggerBadge,
  args: { trigger: "GITHUB_APP" },
} satisfies Meta<typeof ScanTriggerBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvas }) => {
    await expect(canvas.getByText("GitHub App")).toBeVisible();
    await expect(canvas.getByTitle("Started by the GitHub App, for a push or a pull request")).toBeInTheDocument();
  },
};

export const Unknown: Story = {
  args: { trigger: null },
  play: async ({ canvas }) => {
    await expect(canvas.getByLabelText("Unknown trigger")).toHaveTextContent("–");
  },
};

const all: ScanTrigger[] = ["MANUAL", "CHAT", "GITHUB_APP", "UPLOAD"];

export const AllTriggers: Story = {
  render: () => (
    <div className="flex flex-wrap gap-2">
      {all.map((trigger) => (
        <ScanTriggerBadge key={trigger} trigger={trigger} />
      ))}
    </div>
  ),
  play: async ({ canvas }) => {
    for (const label of ["Manual", "Chat", "GitHub App", "Upload"]) {
      await expect(canvas.getByText(label)).toBeVisible();
    }
  },
};
