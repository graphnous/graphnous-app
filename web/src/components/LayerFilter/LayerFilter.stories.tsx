import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, fn, screen, userEvent } from "storybook/test";
import { Field } from "@graphnous/theme";

import type { ScanGraphLayer } from "@/types";

import { LayerFilter, type LayerFilterProps } from "./LayerFilter";

const layers: ScanGraphLayer[] = [
  { id: "result", name: "Scan result" },
  { id: "spring@1.0.0", name: "spring 1.0.0" },
  { id: "jpa@2.1.0", name: "jpa 2.1.0" },
];

function WithState(args: LayerFilterProps) {
  const [shown, setShown] = useState(args.shown);

  return (
    <LayerFilter
      {...args}
      shown={shown}
      onShownChange={(next) => {
        setShown(next);
        args.onShownChange(next);
      }}
    />
  );
}

const meta = {
  title: "Patterns/LayerFilter",
  component: LayerFilter,
  args: { layers, shown: null, onShownChange: fn() },
  render: (args) => (
    <div className="w-56">
      <Field label="Layers">
        <WithState {...args} />
      </Field>
    </div>
  ),
} satisfies Meta<typeof LayerFilter>;

export default meta;
type Story = StoryObj<typeof meta>;

export const AllLayers: Story = {
  play: async ({ canvas }) => {
    await expect(canvas.getByRole("button", { name: /All layers/ })).toBeVisible();
  },
};

export const OnlyAnEnhancement: Story = {
  args: { shown: ["spring@1.0.0"] },
  play: async ({ canvas }) => {
    await expect(canvas.getByRole("button", { name: /spring 1\.0\.0/ })).toBeVisible();
  },
};

/**
 * Leaving the scan result out shows only what the enhancers added; ticking
 * every layer again shows all of them.
 */
export const LeavingTheScanResultOut: Story = {
  play: async ({ args, canvas }) => {
    await userEvent.click(canvas.getByRole("button", { name: /All layers/ }));

    const popover = screen.getByRole("dialog", { name: "Layers" });
    const result = await screen.findByRole("checkbox", { name: "Scan result" });

    await expect(popover).toBeVisible();
    await userEvent.click(result);
    await expect(args.onShownChange).toHaveBeenLastCalledWith(["spring@1.0.0", "jpa@2.1.0"]);
    await expect(canvas.getByRole("button", { name: /spring 1\.0\.0 \+ 1/ })).toBeVisible();

    await userEvent.click(result);
    await expect(args.onShownChange).toHaveBeenLastCalledWith(null);
  },
};

export const ShowingAllAgain: Story = {
  args: { shown: [] },
  play: async ({ args, canvas }) => {
    await userEvent.click(canvas.getByRole("button", { name: /No layers/ }));
    await userEvent.click(await screen.findByRole("button", { name: "Show all" }));

    await expect(args.onShownChange).toHaveBeenLastCalledWith(null);
  },
};
