import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect } from "storybook/test";

import { ChatToolCall } from "./ChatToolCall";
import type { ClassDetails, ScanGraphSummary } from "./toolResults";

const graph: ScanGraphSummary = {
  status: "COMPLETED",
  targets: [
    {
      path: "backend",
      language: "JAVA",
      languageVersion: "25",
      buildSystem: "MAVEN",
      modules: [
        { name: "orders", path: "orders", files: 42, packages: 6, classes: 57, methods: 312, dependencies: 18 },
        { name: "billing", path: "billing", files: 17, packages: 3, classes: 21, methods: 98, dependencies: 9 },
      ],
    },
  ],
};

const order: ClassDetails = {
  name: "OrderController",
  qualifiedName: "com.example.orders.OrderController",
  kind: "CLASS",
  modifiers: ["PUBLIC"],
  typeParameters: [],
  module: "orders",
  file: "src/main/java/com/example/orders/OrderController.java",
  packageName: "com.example.orders",
  superClass: null,
  interfaces: ["com.example.orders.OrderApi"],
  subtypes: [],
  annotations: [
    { name: "RestController", qualifiedName: "org.springframework.web.bind.annotation.RestController" },
    { name: "RequestMapping", arguments: "{\"value\":\"/orders\"}" },
  ],
  methods: [
    {
      name: "getOrder",
      kind: "METHOD",
      modifiers: ["PUBLIC"],
      returnType: "org.springframework.http.ResponseEntity<com.example.orders.Order>",
      parameterNames: ["id"],
      parameterTypes: ["java.util.UUID"],
      annotations: [
        { name: "GetMapping", arguments: "{\"value\":\"/{id}\"}" },
        { name: "PathVariable", parameter: "id" },
      ],
    },
  ],
  fields: [{ name: "orders", type: "com.example.orders.OrderService", modifiers: ["PRIVATE", "FINAL"], annotations: [] }],
};

const meta = {
  title: "Chat/ChatToolCall",
  component: ChatToolCall,
  args: {
    name: "getScanGraph",
    parameters: { scanId: "b3c1" },
    status: "complete",
    result: JSON.stringify(graph),
  },
} satisfies Meta<typeof ChatToolCall>;

export default meta;
type Story = StoryObj<typeof meta>;

export const ScanGraph: Story = {
  play: async ({ canvas }) => {
    await expect(canvas.getByText("Outline of the scan's graph")).toBeVisible();
    await expect(canvas.getByText("billing")).toBeVisible();
  },
};

export const Running: Story = {
  args: { name: "findClasses", parameters: { query: "Order" }, status: "executing", result: undefined },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("Classes matching “Order”…")).toBeVisible();
  },
};

export const Classes: Story = {
  args: {
    name: "findClasses",
    parameters: { query: "Order" },
    result: JSON.stringify([
      { name: "Order", qualifiedName: "com.example.orders.Order", kind: "RECORD", module: "orders" },
      { name: "OrderApi", qualifiedName: "com.example.orders.OrderApi", kind: "INTERFACE", module: "orders" },
      { name: "OrderController", qualifiedName: "com.example.orders.OrderController", kind: "CLASS", module: "orders" },
    ]),
  },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("3 results")).toBeVisible();
  },
};

export const Class: Story = {
  args: {
    name: "getClass",
    parameters: { qualifiedName: order.qualifiedName },
    result: JSON.stringify(order),
  },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("ResponseEntity<Order> getOrder(UUID id)")).toBeVisible();
  },
};

export const Endpoints: Story = {
  args: {
    name: "findAnnotated",
    parameters: { annotation: "GetMapping" },
    result: JSON.stringify([
      {
        kind: "METHOD",
        className: "com.example.orders.OrderController",
        member: "getOrder",
        annotation: { name: "GetMapping", arguments: "{\"value\":\"/{id}\"}" },
      },
    ]),
  },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("@GetMapping(\"/{id}\")")).toBeVisible();
  },
};

export const Dependencies: Story = {
  args: {
    name: "listDependencies",
    parameters: {},
    result: JSON.stringify([
      { module: "orders", name: "org.slf4j:slf4j-api", version: "2.0.18", scope: "compile" },
      { module: "orders", name: "org.junit.jupiter:junit-jupiter", scope: "test" },
    ]),
  },
};

export const Failed: Story = {
  args: { name: "getClass", parameters: { qualifiedName: "com.example.Missing" }, result: "Class com.example.Missing is not in scan b3c1" },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("Class com.example.Missing is not in scan b3c1")).toBeVisible();
  },
};

export const UnknownTool: Story = {
  args: { name: "somethingElse", parameters: {}, result: JSON.stringify({ answer: 42 }) },
};
