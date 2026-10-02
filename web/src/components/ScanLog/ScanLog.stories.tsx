import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, fn, userEvent, waitFor } from "storybook/test";

import { ApiClientProvider } from "@/lib/api/client";
import type { ScanLog as ScanLogLine } from "@/types";

import { ScanLog } from "./ScanLog";

const baseUrl = "http://localhost:8080";

const line = (sequence: number, level: ScanLogLine["level"], message: string): ScanLogLine => ({
  id: `log-${sequence}`,
  scanId: "3f9c",
  sequence,
  timestamp: new Date(Date.UTC(2026, 8, 30, 18, 41, sequence)).toISOString(),
  level,
  message,
});

/**
 * The scan's log so far, in two pages.
 */
const pages: ScanLogLine[][] = [
  [
    line(1, "INFO", "Checking out main at 4e1d2c9"),
    line(2, "DEBUG", "Detected 2 targets"),
    line(3, "INFO", "Scanning graphnous-server"),
  ],
  [line(4, "WARN", "Skipping generated sources"), line(5, "ERROR", "Could not parse Foo.java")],
];

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

/**
 * Answers the log's pages like the API.
 */
const fetchPages = fn(async (input: RequestInfo | URL) => {
  const page = Number(new URL(String(input)).searchParams.get("page"));

  return json({ content: pages[page] ?? [], page, size: 1000, totalElements: 5, totalPages: pages.length });
});

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
    this.dispatchEvent(new MessageEvent(event, { data: JSON.stringify(data) }));
  }

  close() {
    this.readyState = FakeEventSource.CLOSED;
  }
}

const meta = {
  title: "Scan logs/ScanLog",
  component: ScanLog,
  args: { scan: { id: "3f9c", status: "COMPLETED" }, height: "16rem" },
  parameters: { fetchApi: fetchPages },
  decorators: [
    (Story, { parameters }) => (
      <ApiClientProvider baseUrl={baseUrl} fetchApi={parameters.fetchApi}>
        <Story />
      </ApiClientProvider>
    ),
  ],
  beforeEach: () => {
    const original = window.EventSource;
    FakeEventSource.instances = [];
    window.EventSource = FakeEventSource as unknown as typeof EventSource;
    return () => {
      window.EventSource = original;
    };
  },
} satisfies Meta<typeof ScanLog>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * A finished scan: every page of its log, and no stream.
 */
export const Finished: Story = {
  play: async ({ canvas }) => {
    await expect(await canvas.findByText("Could not parse Foo.java")).toBeVisible();
    await expect(canvas.getByText("Checking out main at 4e1d2c9")).toBeVisible();
    await expect(canvas.getAllByRole("listitem")).toHaveLength(5);

    const urls = fetchPages.mock.calls.map(([input]) => String(input));
    await expect(urls).toContain(`${baseUrl}/api/v1/scans/3f9c/logs?page=0&size=1000`);
    await expect(urls).toContain(`${baseUrl}/api/v1/scans/3f9c/logs?page=1&size=1000`);
    await expect(FakeEventSource.instances).toHaveLength(0);
  },
};

export const FilteredByLevel: Story = {
  play: async ({ canvas }) => {
    await canvas.findByText("Could not parse Foo.java");

    await userEvent.selectOptions(canvas.getByRole("combobox", { name: "Level" }), "Warnings and errors");

    await expect(canvas.getAllByRole("listitem")).toHaveLength(2);
    await expect(canvas.getByText("Skipping generated sources")).toBeVisible();
    await expect(canvas.queryByText("Detected 2 targets")).toBeNull();
  },
};

/**
 * A running scan: the lines so far, then new ones from the live stream.
 */
export const Running: Story = {
  args: { scan: { id: "3f9c", status: "RUNNING" } },
  play: async ({ canvas }) => {
    await canvas.findByText("Could not parse Foo.java");
    await expect(canvas.getByText("Connecting")).toBeVisible();

    const source = FakeEventSource.instances.at(-1)!;
    await expect(source.url).toBe(`${baseUrl}/api/v1/scans/3f9c/logs/stream`);

    source.open();
    await expect(await canvas.findByText("Live")).toBeVisible();

    // A line the pages had already is shown once
    source.emit("scanLog", pages[1][1]);
    source.emit("scanLog", line(6, "INFO", "Storing results"));

    await expect(await canvas.findByText("Storing results")).toBeVisible();
    await expect(canvas.getAllByRole("listitem")).toHaveLength(6);
  },
};

const failOnce = fn();

/**
 * The log could not be loaded; trying again loads it.
 */
export const Failed: Story = {
  parameters: {
    fetchApi: async (input: RequestInfo | URL) => {
      if (failOnce.mock.calls.length === 0) {
        failOnce();
        return json({ code: "INTERNAL_ERROR", message: "Boom" }, 500);
      }
      return fetchPages(input);
    },
  },
  beforeEach: () => {
    failOnce.mockClear();
  },
  play: async ({ canvas }) => {
    await expect(await canvas.findByText("Something went wrong on our side")).toBeVisible();

    await userEvent.click(canvas.getByRole("button", { name: "Try again" }));

    await expect(await canvas.findByText("Could not parse Foo.java")).toBeVisible();
    await waitFor(() => expect(canvas.queryByText("Something went wrong on our side")).toBeNull());
  },
};
