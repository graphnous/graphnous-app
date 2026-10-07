import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, fn, waitFor } from "storybook/test";

import type { ScanGraph, ScanGraphNode } from "@/types";

import { NodeDetails } from "./NodeDetails";
import { ScanGraphCanvas, type ScanGraphCanvasProps } from "./ScanGraphCanvas";

const scan = "s1";
const target = `${scan}|backend`;
const orders = `${target}|orders`;
const billing = `${target}|billing`;
const order = `${orders}|class:com.example.Order`;
const entity = `${orders}|class:com.example.Entity`;

function node(id: string, type: ScanGraphNode["type"], name: string, depth: number, properties = {}): ScanGraphNode {
  return { id, type, name, depth, properties };
}

/**
 * Around a class: its file, package, members, annotations and supertype.
 */
const aroundOrder: ScanGraph = {
  scanId: scan,
  focus: order,
  depth: 1,
  truncated: false,
  nodes: [
    node(order, "Class", "Order", 0, {
      qualifiedName: "com.example.Order",
      kind: "CLASS",
      modifiers: ["PUBLIC", "FINAL"],
      annotations: ["jakarta.persistence.Entity"],
    }),
    node(`${orders}|file:src/main/java/com/example/Order.java`, "File", "src/main/java/com/example/Order.java", 1),
    node(`${orders}|package:com.example`, "Package", "com.example", 1),
    node(`${order}|method:total()`, "Method", "total", 1),
    node(`${order}|method:compareTo(Order)`, "Method", "compareTo", 1),
    node(`${order}|field:total`, "Field", "total", 1),
    node(`${order}|annotation:0`, "Annotation", "@Entity", 1),
    node(entity, "Class", "Entity", 1),
  ],
  edges: [
    { source: `${orders}|file:src/main/java/com/example/Order.java`, target: order, type: "DECLARES", properties: {} },
    { source: `${orders}|package:com.example`, target: order, type: "CONTAINS", properties: {} },
    { source: order, target: `${order}|method:total()`, type: "HAS_METHOD", properties: {} },
    { source: order, target: `${order}|method:compareTo(Order)`, type: "HAS_METHOD", properties: {} },
    { source: order, target: `${order}|field:total`, type: "HAS_FIELD", properties: {} },
    { source: order, target: `${order}|annotation:0`, type: "ANNOTATED_WITH", properties: {} },
    { source: order, target: entity, type: "EXTENDS", properties: {} },
  ],
};

/**
 * Around the scan: its target and modules, and a dependency of one.
 */
const aroundScan: ScanGraph = {
  scanId: scan,
  focus: scan,
  depth: 3,
  truncated: false,
  nodes: [
    node(scan, "Scan", "Scan", 0),
    node(target, "ScanTarget", "backend", 1, { language: "JAVA", buildSystem: "MAVEN" }),
    node(orders, "Module", "orders", 2),
    node(billing, "Module", "billing", 2),
    node("dependency:org.slf4j:slf4j-api:2.0.18", "Dependency", "org.slf4j:slf4j-api", 3),
  ],
  edges: [
    { source: scan, target, type: "HAS_TARGET", properties: {} },
    { source: target, target: orders, type: "HAS_MODULE", properties: {} },
    { source: target, target: billing, type: "HAS_MODULE", properties: {} },
    { source: orders, target: "dependency:org.slf4j:slf4j-api:2.0.18", type: "DEPENDS_ON", properties: { scope: "compile" } },
  ],
};

function WithDetails(args: ScanGraphCanvasProps) {
  const [selected, setSelected] = useState<string | null>(args.selected ?? null);
  const node = args.graph.nodes.find((candidate) => candidate.id === selected);

  return (
    <div className="flex gap-4">
      <ScanGraphCanvas
        {...args}
        selected={selected}
        onSelect={(id) => {
          setSelected(id);
          args.onSelect?.(id);
        }}
        className="h-[420px] flex-1 rounded-card border border-border bg-surface"
      />
      <aside className="w-72 rounded-card border border-border bg-surface p-4">
        {node ? (
          <NodeDetails node={node} isFocus={node.id === args.graph.focus} onFocus={args.onFocus ?? (() => {})} />
        ) : (
          "Click a node"
        )}
      </aside>
    </div>
  );
}

const meta = {
  title: "Graph/ScanGraphCanvas",
  component: ScanGraphCanvas,
  args: { graph: aroundOrder, selected: order, onSelect: fn(), onFocus: fn() },
  render: (args) => <WithDetails {...args} />,
} satisfies Meta<typeof ScanGraphCanvas>;

export default meta;
type Story = StoryObj<typeof meta>;

export const AroundAClass: Story = {
  play: async ({ canvas }) => {
    await expect(canvas.getByRole("img", { name: "Graph of 8 nodes and 7 relationships" })).toBeVisible();

    // The canvas is drawn, and the selected node's details shown
    await waitFor(() => expect(canvas.getByRole("img").querySelector("canvas")).not.toBeNull());
    await expect(canvas.getByRole("heading", { name: "Order" })).toBeVisible();
    await expect(canvas.getByRole("button", { name: "In focus" })).toBeDisabled();
  },
};

export const AroundTheScan: Story = {
  args: { graph: aroundScan, selected: target },
  play: async ({ canvas, args }) => {
    await canvas.getByRole("button", { name: "Focus on this node" }).click();
    await expect(args.onFocus).toHaveBeenCalledWith(target);
  },
};

export const AsATree: Story = {
  args: { graph: aroundScan, layout: "tree", selected: null },
};
