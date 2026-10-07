import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, fn, screen, userEvent, waitFor } from "storybook/test";
import { Field } from "@graphnous/theme";

import type { System } from "@/types";

import { SystemSelector, type SystemSelectorProps } from "./SystemSelector";

const times = { createdAt: "2026-09-01T10:00:00Z", updatedAt: "2026-09-30T18:41:07Z" };

const systems: System[] = [
  {
    id: "a1",
    name: "Shop",
    description:
      "The web shop: the storefront, the checkout and the order service behind them, with the payment and shipping integrations",
    ...times,
  },
  { id: "b2", name: "Graphnous", description: "Scans source repositories into a graph", ...times },
  { id: "c3", name: "Pet Clinic", description: null, ...times },
];

function WithState(args: SystemSelectorProps) {
  const [value, setValue] = useState(args.value);

  return (
    <SystemSelector
      {...args}
      value={value}
      onSystemChange={(system) => {
        setValue(system.id);
        args.onSystemChange(system);
      }}
    />
  );
}

const meta = {
  title: "Patterns/SystemSelector",
  component: SystemSelector,
  args: { systems, value: null, onSystemChange: fn() },
  render: (args) => (
    <div className="max-w-sm">
      <Field label="System">
        <WithState {...args} />
      </Field>
    </div>
  ),
} satisfies Meta<typeof SystemSelector>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvas }) => {
    await userEvent.click(canvas.getByRole("combobox", { name: "System" }));

    // The name, with the description below it, cut off to one line
    const shop = screen.getByRole("option", { name: /Shop/ });
    await expect(shop).toHaveTextContent("The web shop");
    await expect(screen.getByText(/^The web shop/)).toHaveClass("truncate");
    // A system without a description shows only its name
    await expect(screen.getByRole("option", { name: "Pet Clinic" })).toBeVisible();
  },
};

export const WithValue: Story = {
  args: { value: "b2" },
  play: async ({ canvas }) => {
    await expect(canvas.getByRole("combobox", { name: "System" })).toHaveValue("Graphnous");
  },
};

export const Searched: Story = {
  play: async ({ args, canvas }) => {
    const input = canvas.getByRole("combobox", { name: "System" });

    await userEvent.type(input, "pet");
    await expect(screen.getAllByRole("option")).toHaveLength(1);

    await userEvent.click(screen.getByRole("option", { name: "Pet Clinic" }));
    await expect(args.onSystemChange).toHaveBeenCalledWith(systems[2]);
    await waitFor(() => expect(screen.queryByRole("listbox")).toBeNull());
    await expect(input).toHaveValue("Pet Clinic");
  },
};

export const NothingMatches: Story = {
  play: async ({ canvas }) => {
    await userEvent.type(canvas.getByRole("combobox", { name: "System" }), "kotlin");

    await expect(screen.getByRole("option", { name: "No systems match" })).toHaveAttribute("aria-disabled", "true");
  },
};

export const WithCreate: Story = {
  args: { onCreateSystem: fn() },
  play: async ({ args, canvas }) => {
    await userEvent.click(canvas.getByRole("combobox", { name: "System" }));

    // The last option, below the systems
    const options = screen.getAllByRole("option");
    await expect(options.at(-1)).toHaveTextContent("Create new system");

    await userEvent.click(screen.getByRole("option", { name: "Create new system" }));
    await expect(args.onCreateSystem).toHaveBeenCalled();
    await expect(args.onSystemChange).not.toHaveBeenCalled();
  },
};
