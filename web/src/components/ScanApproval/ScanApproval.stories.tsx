import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, fn, userEvent, within } from "storybook/test";

import { ScanApproval } from "./ScanApproval";

const meta = {
  title: "Chat/ScanApproval",
  component: ScanApproval,
  args: {
    projectId: "3f2c9a1e-7b44-4d2e-9a51-0c6f1e2b8d17",
    projectName: "Graphnous App",
    branch: "feature/mcp",
    reason: "To compare what the branch changes with the newest scan of main.",
    state: { status: "pending" },
    onApprove: fn(),
    onDecline: fn(),
  },
} satisfies Meta<typeof ScanApproval>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Pending: Story = {
  play: async ({ args, canvasElement }) => {
    const canvas = within(canvasElement);

    await expect(canvas.getByText("Start a scan of Graphnous App?")).toBeInTheDocument();

    // The user scans another branch than the one proposed
    const branch = canvas.getByDisplayValue("feature/mcp");
    await userEvent.clear(branch);
    await userEvent.type(branch, "main");
    await userEvent.click(canvas.getByRole("button", { name: "Start scan" }));

    await expect(args.onApprove).toHaveBeenCalledWith({ branch: "main", revision: undefined });
    await expect(args.onDecline).not.toHaveBeenCalled();
  },
};

export const WithoutABranch: Story = {
  args: { branch: undefined, reason: undefined },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    await expect(canvas.getByRole("button", { name: "Start scan" })).toBeDisabled();
  },
};

export const Declining: Story = {
  play: async ({ args, canvasElement }) => {
    await userEvent.click(within(canvasElement).getByRole("button", { name: "Decline" }));

    await expect(args.onDecline).toHaveBeenCalled();
    await expect(args.onApprove).not.toHaveBeenCalled();
  },
};

export const Starting: Story = {
  args: { state: { status: "starting" } },
};

export const Failed: Story = {
  args: { state: { status: "failed", message: "There is no scanner for language RUST" } },
  play: async ({ canvasElement }) => {
    await expect(within(canvasElement).getByText("There is no scanner for language RUST")).toBeInTheDocument();
  },
};

export const Started: Story = {
  args: {
    state: { status: "started", scanId: "9d1e5c3b-2a6f-4c88-b0e4-7f3a1d2c6b59", branch: "feature/mcp", revision: "cf7ed5b" },
  },
  play: async ({ canvasElement }) => {
    await expect(within(canvasElement).getByRole("link", { name: "Follow the scan" })).toHaveAttribute(
      "href",
      "/projects/3f2c9a1e-7b44-4d2e-9a51-0c6f1e2b8d17/scans/9d1e5c3b-2a6f-4c88-b0e4-7f3a1d2c6b59",
    );
  },
};

export const Declined: Story = {
  args: { state: { status: "declined" } },
};
