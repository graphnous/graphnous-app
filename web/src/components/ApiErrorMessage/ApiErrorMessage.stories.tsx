import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, fn, userEvent } from "storybook/test";

import { FetchError, ResponseError } from "@/generated/api";

import { ApiErrorMessage } from "./ApiErrorMessage";

const response = (status: number, body: unknown) =>
  new ResponseError(
    new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }),
    "Response returned an error code",
  );

const meta = {
  title: "Feedback/ApiErrorMessage",
  component: ApiErrorMessage,
  args: { error: { code: "INTERNAL_ERROR", message: "" } },
} satisfies Meta<typeof ApiErrorMessage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const FromTheApi: Story = {
  args: { error: response(409, { code: "CONFLICT", message: "The scan is still running. Cancel it first." }) },
  play: async ({ canvas }) => {
    // Read from the response body
    await expect(await canvas.findByText("This can't be done right now")).toBeVisible();
    await expect(canvas.getByText("The scan is still running. Cancel it first.")).toBeVisible();
  },
};

export const WithoutABody: Story = {
  args: { error: new ResponseError(new Response("<html>Bad gateway</html>", { status: 404 })) },
  play: async ({ canvas }) => {
    await expect(await canvas.findByText("Not found")).toBeVisible();
  },
};

export const Validation: Story = {
  args: { error: { code: "VALIDATION_ERROR", message: "gitUrl: must be a git URL", status: 400 } },
};

export const Forbidden: Story = {
  args: { error: { code: "FORBIDDEN", message: "", status: 403 } },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("Ask an owner of the organization for access.")).toBeVisible();
  },
};

export const PlanLimit: Story = {
  args: { error: { code: "PLAN_LIMIT", message: "Your plan includes 3 projects.", status: 403 } },
  play: async ({ canvas }) => {
    await expect(canvas.getByText("Your plan includes 3 projects. Upgrade your plan to continue.")).toBeVisible();
  },
};

export const NotFound: Story = {
  args: {
    error: { code: "NOT_FOUND", message: "Project not found.", status: 404 },
    backHref: "/projects",
    backLabel: "Back to projects",
  },
  play: async ({ canvas }) => {
    await expect(canvas.getByRole("link", { name: "Back to projects" })).toHaveAttribute("href", "/projects");
  },
};

export const ServerUnreachable: Story = {
  args: { error: new FetchError(new TypeError("Failed to fetch")), onRetry: fn(), announce: true },
  play: async ({ args, canvas }) => {
    await expect(await canvas.findByRole("alert")).toHaveTextContent("Couldn't reach the server");

    await userEvent.click(canvas.getByRole("button", { name: "Try again" }));
    await expect(args.onRetry).toHaveBeenCalledOnce();
  },
};

export const ServerError: Story = {
  args: { error: response(500, { code: "INTERNAL_ERROR", message: "NullPointerException" }), onRetry: fn() },
  play: async ({ canvas }) => {
    await expect(await canvas.findByText("Something went wrong on our side")).toBeVisible();
    // Not the internals
    await expect(canvas.queryByText("NullPointerException")).toBeNull();
  },
};

export const NoRetryForConflicts: Story = {
  args: { error: { code: "CONFLICT", message: "A project with this name already exists." }, onRetry: fn() },
  play: async ({ canvas }) => {
    await expect(canvas.queryByRole("button", { name: "Try again" })).toBeNull();
  },
};
