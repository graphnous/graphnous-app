import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect } from "storybook/test";

import { ScanRevision, revisionLabel } from "./ScanRevision";

const COMMIT = "4f2a9c1e88d0a19c3e7f0b42c0ffee1234567890";

const meta = {
  title: "Patterns/ScanRevision",
  component: ScanRevision,
} satisfies Meta<typeof ScanRevision>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * A scan of the tip of a branch, once it has checked it out.
 */
export const Commit: Story = {
  args: { revision: COMMIT, requestedRevision: null },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("4f2a9c1")).toBeVisible();
    await expect(canvas.getByTitle(COMMIT)).toBeVisible();
  },
};

/**
 * A scan asked for a tag: the tag, then the commit it was of.
 */
export const Tag: Story = {
  args: { revision: COMMIT, requestedRevision: "v1.2.0" },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("v1.2.0 · 4f2a9c1")).toBeVisible();
  },
};

/**
 * A scan of the tip of a branch, before it has checked it out.
 */
export const Tip: Story = {
  args: { revision: null, requestedRevision: null },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("tip")).toBeVisible();
  },
};

export const Labels: Story = {
  args: { revision: COMMIT },
  play: async () => {
    // Before checking out, what it was asked for
    await expect(revisionLabel({ revision: null, requestedRevision: "v1.2.0" })).toBe("v1.2.0");
    // A short or full hash of the commit says nothing more
    await expect(revisionLabel({ revision: COMMIT, requestedRevision: "4f2a9c1" })).toBe("4f2a9c1");
    await expect(revisionLabel({ revision: COMMIT, requestedRevision: COMMIT })).toBe("4f2a9c1");
    // An uploaded scan's revision that is not a hash, as it is
    await expect(revisionLabel({ revision: "release-42", requestedRevision: "release-42" })).toBe("release-42");
    await expect(revisionLabel({ revision: null, requestedRevision: null })).toBeNull();
  },
};
