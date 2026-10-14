import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { Field } from "@graphnous/theme";
import { useState } from "react";
import { expect, userEvent, within } from "storybook/test";

import { MultiSelect, type MultiSelectOption } from "./MultiSelect";

const ENHANCERS: MultiSelectOption[] = [
  { value: "spring", label: "spring", description: "1.0.0" },
  { value: "angular", label: "angular", description: "1.1.0" },
  { value: "react", label: "react", description: "1.0.0" },
  { value: "nextjs", label: "nextjs", description: "1.0.0" },
  { value: "fetch-browser", label: "fetch-browser", description: "1.1.0" },
];

function Chooser({ initial, disabled }: { initial: string[]; disabled?: boolean }) {
  const [value, setValue] = useState(initial);

  return (
    <div className="max-w-md">
      <Field label="Enhancers">
        <MultiSelect
          options={ENHANCERS}
          value={value}
          onValueChange={setValue}
          placeholder="None"
          disabled={disabled}
        />
      </Field>
      <p data-testid="chosen">{value.join(",")}</p>
    </div>
  );
}

const meta = {
  title: "Components/MultiSelect",
  component: Chooser,
  parameters: { layout: "padded" },
} satisfies Meta<typeof Chooser>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * Every option chosen, as enhancers are when a scan is created.
 */
export const AllChosen: Story = {
  args: { initial: ENHANCERS.map((option) => option.value) },
  play: async ({ canvas }) => {
    await expect(canvas.getByRole("button", { name: "Remove spring" })).toBeVisible();
    await expect(canvas.getAllByRole("button", { name: /^Remove / })).toHaveLength(5);
  },
};

/**
 * Removing a chip, and the last one with Backspace.
 */
export const Removing: Story = {
  args: { initial: ["spring", "react", "nextjs"] },
  play: async ({ canvas }) => {
    await userEvent.click(canvas.getByRole("button", { name: "Remove react" }));
    await expect(canvas.getByTestId("chosen")).toHaveTextContent("spring,nextjs");

    await userEvent.click(canvas.getByRole("combobox", { name: "Enhancers" }));
    await userEvent.keyboard("{Escape}{Backspace}");
    await expect(canvas.getByTestId("chosen")).toHaveTextContent(/^spring$/);
  },
};

/**
 * Choosing from the list, which stays open, in the order of the options
 * whatever order they are chosen in.
 */
export const Choosing: Story = {
  args: { initial: [] },
  play: async ({ canvas, canvasElement }) => {
    await expect(canvas.getByPlaceholderText("None")).toBeVisible();

    await userEvent.click(canvas.getByRole("combobox", { name: "Enhancers" }));

    // The list is portalled to the body
    const list = within(canvasElement.ownerDocument.body);
    await userEvent.click(await list.findByRole("option", { name: /nextjs/ }));
    await userEvent.click(list.getByRole("option", { name: /spring/ }));

    await expect(canvas.getByTestId("chosen")).toHaveTextContent("spring,nextjs");
    await expect(list.getByRole("option", { name: /spring/ })).toHaveAttribute("aria-selected", "true");

    // Unchosen again with the keyboard
    await userEvent.keyboard("spr{Enter}");
    await expect(canvas.getByTestId("chosen")).toHaveTextContent(/^nextjs$/);
  },
};

/**
 * Narrowed down by typing.
 */
export const Filtering: Story = {
  args: { initial: [] },
  play: async ({ canvas, canvasElement }) => {
    await userEvent.type(canvas.getByRole("combobox", { name: "Enhancers" }), "fetch");

    const list = within(canvasElement.ownerDocument.body);
    await expect(await list.findAllByRole("option")).toHaveLength(1);

    await userEvent.keyboard("{Enter}");
    await expect(canvas.getByTestId("chosen")).toHaveTextContent("fetch-browser");
  },
};

export const Disabled: Story = {
  args: { initial: ["spring"], disabled: true },
  play: async ({ canvas }) => {
    await expect(canvas.getByRole("combobox", { name: "Enhancers" })).toBeDisabled();
    await expect(canvas.getByRole("button", { name: "Remove spring" })).toBeDisabled();
  },
};
