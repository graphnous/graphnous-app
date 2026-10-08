import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, userEvent } from "storybook/test";

import type { ScanComparison } from "@/types";

import { ScanComparisonView } from "./ScanComparison";

const orders = "backend|orders";

const comparison: ScanComparison = {
  base: "s1",
  head: "s2",
  summary: [
    { type: "Class", added: 1, removed: 1, changed: 1 },
    { type: "Method", added: 2, removed: 0, changed: 0 },
    { type: "Dependency", added: 0, removed: 0, changed: 1 },
  ],
  added: [
    { key: `${orders}|class:com.example.Invoice`, type: "Class", name: "Invoice", properties: { kind: "CLASS" } },
    { key: `${orders}|class:com.example.Invoice|method:com.example.Invoice.total()`, type: "Method", name: "total", properties: {} },
    { key: `${orders}|class:com.example.Order|method:com.example.Order.cancel()`, type: "Method", name: "cancel", properties: {} },
  ],
  removed: [
    { key: `${orders}|class:com.example.LegacyOrder`, type: "Class", name: "LegacyOrder", properties: { kind: "CLASS" } },
  ],
  changed: [
    {
      key: `${orders}|class:com.example.Order`,
      type: "Class",
      name: "Order",
      properties: [
        { name: "interfaces", before: ["com.example.Identified"], after: ["com.example.Identified", "java.io.Serializable"] },
        { name: "superClass", before: "com.example.Entity", after: null },
      ],
    },
    {
      key: `${orders}|dependency:org.slf4j:slf4j-api`,
      type: "Dependency",
      name: "org.slf4j:slf4j-api",
      properties: [{ name: "version", before: "2.0.17", after: "2.0.18" }],
    },
  ],
  truncated: false,
};

const meta = {
  title: "Patterns/ScanComparison",
  component: ScanComparisonView,
  args: { comparison },
} satisfies Meta<typeof ScanComparisonView>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvas }) => {
    const summary = canvas.getByRole("table", { name: "Changes by type" });
    await expect(summary).toHaveTextContent("+2");

    // The added nodes first, with the counts of all of them on the tabs
    await expect(canvas.getByRole("tab", { name: "Added (3)" })).toBeVisible();
    await expect(canvas.getByText("Invoice")).toBeVisible();

    await userEvent.click(canvas.getByRole("tab", { name: "Changed (2)" }));
    await expect(canvas.getByText("com.example.Entity")).toBeVisible();
    await expect(canvas.getByText("2.0.17")).toBeVisible();
    await expect(canvas.getByText("com.example.Identified, java.io.Serializable")).toBeVisible();
  },
};

export const Truncated: Story = {
  args: { comparison: { ...comparison, truncated: true } },
  play: async ({ canvas }) => {
    await expect(canvas.getByText(/more changes than fit/)).toBeVisible();
  },
};

export const NoDifferences: Story = {
  args: { comparison: { ...comparison, summary: [], added: [], removed: [], changed: [] } },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("No differences")).toBeVisible();
  },
};
