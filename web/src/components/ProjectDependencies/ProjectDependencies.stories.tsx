import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, fn, userEvent } from "storybook/test";
import type { Sort } from "@graphnous/theme";

import type { ScanComparison, ScanDependency, ScanDependencySort } from "@/types";

import { DependenciesTable, DependencyChanges, type DependenciesTableProps } from "./ProjectDependencies";

const dependencies: ScanDependency[] = [
  { id: "dependency:com.fasterxml.jackson.core:jackson-databind:2.22.2", module: "orders", name: "com.fasterxml.jackson.core:jackson-databind", version: "2.22.2", scope: "compile" },
  { id: "dependency:org.junit.jupiter:junit-jupiter:", module: "orders", name: "org.junit.jupiter:junit-jupiter", scope: "test" },
  { id: "dependency:org.slf4j:slf4j-api:2.0.18", module: "orders", name: "org.slf4j:slf4j-api", version: "2.0.18", scope: "compile" },
];

function WithSort(args: DependenciesTableProps) {
  const [sort, setSort] = useState<Sort<ScanDependencySort>>(args.sort);

  return (
    <DependenciesTable
      {...args}
      sort={sort}
      onSortChange={(next) => {
        setSort(next);
        args.onSortChange(next);
      }}
    />
  );
}

const meta = {
  title: "Patterns/DependenciesTable",
  component: DependenciesTable,
  args: {
    page: { content: dependencies, page: 0, size: 50, totalElements: 3, totalPages: 1 },
    sort: { field: "name", direction: "asc" },
    onSortChange: fn(),
  },
  render: (args) => <WithSort {...args} />,
} satisfies Meta<typeof DependenciesTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvas, args }) => {
    await expect(canvas.getByText("org.slf4j:slf4j-api")).toBeVisible();
    await expect(canvas.getByText("2.0.18")).toBeVisible();

    // A library without a version shows a dash
    const junit = canvas.getByText("org.junit.jupiter:junit-jupiter").closest("tr");
    await expect(junit).toHaveTextContent("—");

    await userEvent.click(canvas.getByRole("button", { name: /Scope/ }));
    await expect(args.onSortChange).toHaveBeenCalledWith({ field: "scope", direction: "asc" });
  },
};

export const NoneMatch: Story = {
  args: {
    page: { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 },
    searching: true,
  },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("No dependencies match")).toBeVisible();
  },
};

const comparison: ScanComparison = {
  base: "s1",
  head: "s2",
  summary: [{ type: "Dependency", added: 1, removed: 1, changed: 1 }],
  added: [
    { key: "orders|dependency:com.fasterxml.jackson.core:jackson-databind", type: "Dependency", name: "com.fasterxml.jackson.core:jackson-databind", properties: { version: "2.22.2" } },
    // Not a dependency, so not listed
    { key: "orders|class:com.example.Invoice", type: "Class", name: "Invoice", properties: {} },
  ],
  removed: [
    { key: "orders|dependency:commons-lang:commons-lang", type: "Dependency", name: "commons-lang:commons-lang", properties: { version: "2.6" } },
  ],
  changed: [
    {
      key: "orders|dependency:org.slf4j:slf4j-api",
      type: "Dependency",
      name: "org.slf4j:slf4j-api",
      properties: [{ name: "version", before: "2.0.17", after: "2.0.18" }],
    },
  ],
  truncated: false,
};

export const Changes: StoryObj<typeof DependencyChanges> = {
  render: () => <DependencyChanges comparison={comparison} />,
  play: async ({ canvas }) => {
    await expect(canvas.getByText("com.fasterxml.jackson.core:jackson-databind 2.22.2")).toBeVisible();
    await expect(canvas.getByText("commons-lang:commons-lang 2.6")).toBeVisible();
    await expect(canvas.getByText("version 2.0.17 → 2.0.18")).toBeVisible();
    await expect(canvas.queryByText("Invoice")).not.toBeInTheDocument();
  },
};

export const NoChanges: StoryObj<typeof DependencyChanges> = {
  render: () => (
    <DependencyChanges comparison={{ ...comparison, summary: [], added: [], removed: [], changed: [] }} />
  ),
  play: async ({ canvas }) => {
    await expect(canvas.getByText("The same libraries as the scan before it.")).toBeVisible();
  },
};
