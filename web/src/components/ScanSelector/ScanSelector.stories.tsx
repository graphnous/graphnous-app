import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, fn, screen, userEvent } from "storybook/test";
import { Field } from "@graphnous/theme";

import type { Scan } from "@/types";

import { ScanSelector, type ScanSelectorProps } from "./ScanSelector";

function scan(id: string, status: Scan["status"], branch: string, revision: string, createdAt: string): Scan {
  return { id, projectId: "p1", status, branch, revision, createdAt, updatedAt: createdAt };
}

const scans: Scan[] = [
  scan("s1", "COMPLETED", "main", "4f2a9c1e88d0aaaaaaaaaaaaaaaaaaaaaaaaaaaa", "2026-10-01T09:00:00Z"),
  scan("s2", "FAILED", "main", "77b01d3a5c2eaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "2026-10-02T09:00:00Z"),
  scan("s3", "COMPLETED", "feature/graph", "a19c3e7f0b42aaaaaaaaaaaaaaaaaaaaaaaaaaaa", "2026-10-03T09:00:00Z"),
  scan("s4", "RUNNING", "main", "c0ffee123456aaaaaaaaaaaaaaaaaaaaaaaaaaaa", "2026-10-04T09:00:00Z"),
];

function WithState(args: ScanSelectorProps) {
  const [value, setValue] = useState(args.value);

  return (
    <ScanSelector
      {...args}
      value={value}
      onScanChange={(scan) => {
        setValue(scan.id);
        args.onScanChange(scan);
      }}
    />
  );
}

const meta = {
  title: "Patterns/ScanSelector",
  component: ScanSelector,
  args: { scans, value: "s3", onScanChange: fn() },
  render: (args) => (
    <div className="max-w-xs">
      <Field label="Scan">
        <WithState {...args} />
      </Field>
    </div>
  ),
} satisfies Meta<typeof ScanSelector>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvas, args }) => {
    await userEvent.click(canvas.getByRole("combobox", { name: "Scan" }));

    // Only the completed scans, the newest first
    const options = screen.getAllByRole("option");
    await expect(options).toHaveLength(2);
    await expect(options[0]).toHaveTextContent("feature/graph · a19c3e7");
    await expect(options[1]).toHaveTextContent("main · 4f2a9c1");

    await userEvent.click(options[1]);
    await expect(args.onScanChange).toHaveBeenCalledWith(scans[0]);
  },
};

export const NothingChosen: Story = {
  args: { value: null },
};
