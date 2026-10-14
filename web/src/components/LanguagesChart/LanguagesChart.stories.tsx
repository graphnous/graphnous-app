import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect } from "storybook/test";

import { LanguagesChart } from "./LanguagesChart";

const meta = {
  title: "Components/LanguagesChart",
  component: LanguagesChart,
  parameters: { layout: "padded" },
} satisfies Meta<typeof LanguagesChart>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * A Java backend with a TypeScript frontend.
 */
export const TwoLanguages: Story = {
  args: { languages: { JAVA: 1240, TYPESCRIPT: 386 } },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("1,626")).toBeVisible();
    await expect(canvas.getByText("1,240 files (76%)")).toBeVisible();
    await expect(canvas.getByRole("img", { name: /Java 76%, TypeScript 24%/ })).toBeInTheDocument();
  },
};

/**
 * Every known language, and files of a target without one, folded into
 * Other.
 */
export const EveryLanguage: Story = {
  args: { languages: { JAVA: 420, TYPESCRIPT: 310, JAVASCRIPT: 80, PYTHON: 150, GO: 60, RUST: 40, UNKNOWN: 12 } },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("Other")).toBeVisible();
  },
};

/**
 * A single file: singular wording.
 */
export const OneFile: Story = {
  args: { languages: { PYTHON: 1 } },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("1 file (100%)")).toBeVisible();
  },
};

/**
 * No files at all.
 */
export const Empty: Story = {
  args: { languages: {} },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("None")).toBeVisible();
  },
};
