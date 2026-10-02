import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, within } from "storybook/test";

import type { ScanStep, ScanStepStatus, ScanStepType } from "@/types";

import { ScanSteps } from "./ScanSteps";

const types: ScanStepType[] = ["CHECKOUT", "PLAN", "SCAN", "STORE", "ENHANCE_RESULTS", "ENHANCE_SCAN"];

/**
 * The six steps with these statuses, a few seconds each.
 */
function stepsWith(statuses: ScanStepStatus[], errors: Partial<Record<ScanStepType, string>> = {}): ScanStep[] {
  return types.map((type, index) => {
    const status = statuses[index];
    const started = status === "PENDING" || status === "SKIPPED" ? null : `2026-09-30T18:41:${String(index * 5).padStart(2, "0")}Z`;
    const finished = status === "COMPLETED" || status === "FAILED" ? `2026-09-30T18:41:${String(index * 5 + 4).padStart(2, "0")}Z` : null;

    return { type, status, startedAt: started, finishedAt: finished, error: errors[type] ?? null };
  });
}

const meta = {
  title: "Scan execution/ScanSteps",
  component: ScanSteps,
  args: { steps: stepsWith(["COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED"]) },
  render: (args) => (
    <div className="max-w-2xl">
      <ScanSteps {...args} />
    </div>
  ),
} satisfies Meta<typeof ScanSteps>;

export default meta;
type Story = StoryObj<typeof meta>;

const items = (canvas: ReturnType<typeof within>) =>
  within(canvas.getByRole("list", { name: "Scan steps" })).getAllByRole("listitem");

export const Completed: Story = {
  play: async ({ canvas }) => {
    const steps = items(canvas);

    await expect(steps).toHaveLength(6);
    await expect(steps[0]).toHaveTextContent("Check out the repository");
    await expect(steps[5]).toHaveTextContent("Enhance the scan");
    await expect(steps[0]).toHaveTextContent("4 s");
  },
};

export const Running: Story = {
  args: { steps: stepsWith(["COMPLETED", "COMPLETED", "RUNNING", "PENDING", "PENDING", "PENDING"]) },
  play: async ({ canvas }) => {
    const steps = items(canvas);

    await expect(steps[2]).toHaveAttribute("aria-current", "step");
    await expect(steps[2]).toHaveTextContent("Scan the targets");
  },
};

/**
 * A step failed: its error, and the steps after it did not run.
 */
export const Failed: Story = {
  args: {
    steps: stepsWith(["COMPLETED", "FAILED", "SKIPPED", "SKIPPED", "SKIPPED", "SKIPPED"], {
      PLAN: "No Maven or npm project found in backend",
    }),
  },
  play: async ({ canvas }) => {
    const steps = items(canvas);

    await expect(within(steps[1]).getByText("Finding the targets to scan failed")).toBeVisible();
    await expect(within(steps[1]).getByText("No Maven or npm project found in backend")).toBeVisible();
    await expect(steps[2]).toHaveTextContent("Did not run, as finding the targets to scan failed.");
    await expect(steps[5]).toHaveTextContent("Did not run, as finding the targets to scan failed.");
  },
};

/**
 * Results scanned elsewhere and uploaded: the first three steps are
 * skipped, without anything failing.
 */
export const Uploaded: Story = {
  args: { steps: stepsWith(["SKIPPED", "SKIPPED", "SKIPPED", "COMPLETED", "COMPLETED", "RUNNING"]) },
  play: async ({ canvas }) => {
    const steps = items(canvas);

    for (const step of steps.slice(0, 3)) {
      await expect(step).toHaveTextContent("Did not run: the results were scanned elsewhere and uploaded.");
    }
    await expect(steps[3]).not.toHaveTextContent("Did not run");
  },
};

/**
 * A failed enhance step leaves the scan completed.
 */
export const EnhanceFailed: Story = {
  args: {
    steps: stepsWith(["COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "FAILED", "SKIPPED"], {
      ENHANCE_RESULTS: "Rule spring-endpoints matched no nodes",
    }),
  },
  play: async ({ canvas }) => {
    const steps = items(canvas);

    await expect(within(steps[4]).getByText(/the scan is complete/)).toBeVisible();
    await expect(steps[5]).toHaveTextContent("Did not run, as enhancing the results failed.");
  },
};

/**
 * Steps from the API in another order are shown in the order they run.
 */
export const Unordered: Story = {
  args: { steps: stepsWith(["COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED"]).reverse() },
  play: async ({ canvas }) => {
    const steps = items(canvas);

    await expect(steps[0]).toHaveTextContent("Check out the repository");
    await expect(steps[5]).toHaveTextContent("Enhance the scan");
  },
};
