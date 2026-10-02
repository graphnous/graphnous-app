import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, userEvent, waitFor } from "storybook/test";
import { Button, LogViewer, type LogLine } from "@graphnous/theme";

import { useEventStream } from "./useEventStream";

/**
 * An EventSource the story controls: the browser's needs a server.
 */
class FakeEventSource extends EventTarget {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;
  static instances: FakeEventSource[] = [];

  readyState = FakeEventSource.CONNECTING;

  constructor(readonly url: string) {
    super();
    FakeEventSource.instances.push(this);
  }

  open() {
    this.readyState = FakeEventSource.OPEN;
    this.dispatchEvent(new Event("open"));
  }

  emit(event: string, data: unknown) {
    this.dispatchEvent(new MessageEvent(event, { data: typeof data === "string" ? data : JSON.stringify(data) }));
  }

  close() {
    this.readyState = FakeEventSource.CLOSED;
  }
}

const latest = () => FakeEventSource.instances.at(-1)!;

function LiveLogs() {
  const [scanId, setScanId] = useState<string | null>("3f9c");
  const [lines, setLines] = useState<LogLine[]>([]);
  const status = useEventStream<LogLine>(scanId && `http://localhost:8080/api/v1/scans/${scanId}/logs/stream`, {
    event: "scanLog",
    onEvent: (line) => setLines((all) => [...all, line]),
  });

  return (
    <div className="flex flex-col items-start gap-2 text-sm">
      <p>Stream: {status}</p>
      <Button size="sm" variant="secondary" onClick={() => setScanId(null)}>
        Stop
      </Button>
      <LogViewer label="Scan logs" lines={lines} height="10rem" className="w-full" />
    </div>
  );
}

const meta = {
  title: "Patterns/useEventStream",
  component: LiveLogs,
  beforeEach: () => {
    const original = window.EventSource;
    FakeEventSource.instances = [];
    window.EventSource = FakeEventSource as unknown as typeof EventSource;
    return () => {
      window.EventSource = original;
    };
  },
} satisfies Meta<typeof LiveLogs>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Streaming: Story = {
  play: async ({ canvas }) => {
    await expect(canvas.getByText("Stream: connecting")).toBeVisible();
    await expect(latest().url).toBe("http://localhost:8080/api/v1/scans/3f9c/logs/stream");

    latest().open();
    await waitFor(() => expect(canvas.getByText("Stream: open")).toBeVisible());

    latest().emit("scanLog", { level: "INFO", message: "Scanning graphnous-server", timestamp: "2026-09-30T18:41:07Z" });
    // Other events, and data that is not JSON, are skipped
    latest().emit("message", { level: "INFO", message: "Not a scan log" });
    latest().emit("scanLog", "not json");
    latest().emit("scanLog", { level: "WARN", message: "Skipping generated sources" });

    await waitFor(() => expect(canvas.getAllByRole("listitem")).toHaveLength(2));
    await expect(canvas.getByText("Scanning graphnous-server")).toBeVisible();
    await expect(canvas.queryByText("Not a scan log")).toBeNull();
  },
};

export const Closed: Story = {
  play: async ({ canvas }) => {
    const source = latest();
    source.open();

    await userEvent.click(canvas.getByRole("button", { name: "Stop" }));

    await expect(source.readyState).toBe(FakeEventSource.CLOSED);
    await expect(canvas.getByText("Stream: closed")).toBeVisible();
  },
};
