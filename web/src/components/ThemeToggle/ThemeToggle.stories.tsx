import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, userEvent, within } from "storybook/test";

import { THEME_STORAGE_KEY } from "@/lib/theme/theme";

import { ThemeToggle } from "./ThemeToggle";

const meta = {
  title: "Components/ThemeToggle",
  component: ThemeToggle,
  parameters: { layout: "centered" },
  // Each story starts from the system's colour scheme
  beforeEach: () => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.removeAttribute("data-theme");

    return () => {
      localStorage.removeItem(THEME_STORAGE_KEY);
      document.documentElement.removeAttribute("data-theme");
    };
  },
} satisfies Meta<typeof ThemeToggle>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * Following the system's colour scheme, as without a choice.
 */
export const System: Story = {
  play: async ({ canvas }) => {
    await expect(canvas.getByRole("button", { name: "Theme: System" })).toBeVisible();
    await expect(document.documentElement).not.toHaveAttribute("data-theme");
  },
};

/**
 * Choosing dark, then the system's again.
 */
export const Choosing: Story = {
  play: async ({ canvas, canvasElement }) => {
    const menu = within(canvasElement.ownerDocument.body);

    await userEvent.click(canvas.getByRole("button", { name: "Theme: System" }));
    await userEvent.click(await menu.findByRole("menuitem", { name: "Dark" }));

    await expect(document.documentElement).toHaveAttribute("data-theme", "dark");
    await expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe("dark");
    await expect(canvas.getByRole("button", { name: "Theme: Dark" })).toBeVisible();

    await userEvent.click(canvas.getByRole("button", { name: "Theme: Dark" }));
    await userEvent.click(await menu.findByRole("menuitem", { name: "System" }));

    await expect(document.documentElement).not.toHaveAttribute("data-theme");
    await expect(localStorage.getItem(THEME_STORAGE_KEY)).toBeNull();
  },
};

/**
 * A choice made before, such as on an earlier visit.
 */
export const ChosenBefore: Story = {
  beforeEach: () => {
    localStorage.setItem(THEME_STORAGE_KEY, "light");
  },
  play: async ({ canvas }) => {
    await expect(await canvas.findByRole("button", { name: "Theme: Light" })).toBeVisible();
    await expect(document.documentElement).toHaveAttribute("data-theme", "light");
  },
};
