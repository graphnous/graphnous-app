import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, userEvent, waitFor } from "storybook/test";
import { Button, StatusIndicator } from "graphnous-theme";

import { usePolling } from "./usePolling";

type Scan = { status: "RUNNING" | "COMPLETED" | "FAILED"; request: number };

/**
 * A scan that completes on the third request, or fails on the second when
 * failing.
 */
function ScanStatus({ failOnce = false }: { failOnce?: boolean }) {
  const [requests, setRequests] = useState(0);

  const { data, error, polling, refresh } = usePolling<Scan>(
    async () => {
      const request = requests + 1;
      setRequests(request);
      if (failOnce && request === 2) {
        throw new Error("Network down");
      }
      return { status: request % 3 === 0 ? "COMPLETED" : "RUNNING", request };
    },
    { interval: 50, until: (scan) => scan.status !== "RUNNING" },
  );

  return (
    <div className="flex flex-col items-start gap-2 text-sm">
      {data && (
        <StatusIndicator tone={data.status === "COMPLETED" ? "success" : "info"} active={data.status === "RUNNING"}>
          {data.status}
        </StatusIndicator>
      )}
      <p>Requests: {requests}</p>
      <p>{polling ? "Polling" : "Finished"}</p>
      {Boolean(error) && <p>The last request failed</p>}
      <Button size="sm" variant="secondary" onClick={refresh}>
        Scan again
      </Button>
    </div>
  );
}

const meta = {
  title: "Patterns/usePolling",
  component: ScanStatus,
} satisfies Meta<typeof ScanStatus>;

export default meta;
type Story = StoryObj<typeof meta>;

export const StopsWhenFinished: Story = {
  play: async ({ canvas }) => {
    await waitFor(() => expect(canvas.getByText("COMPLETED")).toBeVisible());
    await expect(canvas.getByText("Finished")).toBeVisible();
    await expect(canvas.getByText("Requests: 3")).toBeVisible();

    // No more requests once it is finished
    await new Promise((resolve) => setTimeout(resolve, 200));
    await expect(canvas.getByText("Requests: 3")).toBeVisible();
  },
};

export const RefreshedAfterFinishing: Story = {
  play: async ({ canvas }) => {
    await waitFor(() => expect(canvas.getByText("Finished")).toBeVisible());

    await userEvent.click(canvas.getByRole("button", { name: "Scan again" }));
    await waitFor(() => expect(canvas.getByText("Requests: 6")).toBeVisible());
    await expect(canvas.getByText("Finished")).toBeVisible();
  },
};

export const GoesOnAfterAnError: Story = {
  args: { failOnce: true },
  play: async ({ canvas }) => {
    await waitFor(() => expect(canvas.getByText("COMPLETED")).toBeVisible());
    // The error is cleared by the next request that works
    await expect(canvas.queryByText("The last request failed")).toBeNull();
  },
};
