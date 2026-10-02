import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect } from "storybook/test";

import { ScanStepError } from "./ScanStepError";

const stackTrace = [
  "org.eclipse.jgit.api.errors.TransportException: https://github.com/example/shop.git: Authentication is required",
  ...Array.from({ length: 40 }, (_, index) => `\tat org.eclipse.jgit.transport.Frame${index}.call(Frame${index}.java:${100 + index})`),
].join("\n");

const meta = {
  title: "Scan execution/ScanStepError",
  component: ScanStepError,
  args: { step: { type: "PLAN", error: "No Maven or npm project found in backend" } },
  render: (args) => (
    <div className="max-w-2xl">
      <ScanStepError {...args} />
    </div>
  ),
} satisfies Meta<typeof ScanStepError>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvas }) => {
    await expect(canvas.getByText("Finding the targets to scan failed")).toBeVisible();
    await expect(canvas.getByText("No Maven or npm project found in backend")).toBeVisible();
    await expect(canvas.getByRole("button", { name: "Copy the error" })).toBeVisible();
  },
};

/**
 * A long error, such as a stack trace, scrolls within the alert.
 */
export const LongError: Story = {
  args: { step: { type: "CHECKOUT", error: stackTrace } },
  play: async ({ canvas }) => {
    const details = canvas.getByRole("region", { name: "Error details" });

    await expect(details.scrollHeight).toBeGreaterThan(details.clientHeight);
  },
};

/**
 * A failed enhance step does not fail the scan.
 */
export const EnhanceStep: Story = {
  args: { step: { type: "ENHANCE_SCAN", error: "Rule spring-endpoints matched no nodes" } },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("Enhancing the scan failed")).toBeVisible();
    await expect(canvas.getByText(/the scan is complete/)).toBeVisible();
  },
};

export const WithoutError: Story = {
  args: { step: { type: "SCAN", error: null } },
  play: async ({ canvasElement }) => {
    await expect(canvasElement.querySelector('[role="alert"], [role="status"], [role="region"]')).toBeNull();
  },
};
