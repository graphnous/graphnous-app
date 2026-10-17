import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, fn, userEvent, within } from "storybook/test";

import { GitHubAppSettings } from "./GitHubAppSettings";

const none = {
  source: null,
  appId: null,
  slug: null,
  name: null,
  htmlUrl: null,
  installUrl: null,
  error: null,
  blocked: null,
  serverUrl: null,
} as const;

const connected = {
  ...none,
  source: "SETUP",
  appId: "777",
  slug: "graphnous-acme",
  name: "Graphnous Acme",
  htmlUrl: "https://github.com/apps/graphnous-acme",
  installUrl: "https://github.com/apps/graphnous-acme/installations/new",
  serverUrl: "https://graphnous.acme.test",
} as const;

const meta = {
  title: "Settings/GitHubAppSettings",
  component: GitHubAppSettings,
  args: {
    status: none,
    onRegister: fn(async () => {}),
    onDisconnect: fn(async () => {}),
  },
} satisfies Meta<typeof GitHubAppSettings>;

export default meta;

type Story = StoryObj<typeof meta>;

export const NotSetUp: Story = {
  play: async ({ args, canvasElement }) => {
    const canvas = within(canvasElement);
    const create = canvas.getByRole("button", { name: "Create on GitHub" });

    // Where GitHub reaches the server is needed, as the configuration does not say
    await expect(create).toBeDisabled();

    await userEvent.type(canvas.getByPlaceholderText("https://graphnous.example.com"), "http://localhost:1337");
    await userEvent.type(canvas.getAllByPlaceholderText("Optional")[1], "https://smee.io/aBcD1234");
    await userEvent.click(create);

    await expect(args.onRegister).toHaveBeenCalledWith({
      name: "Graphnous",
      organization: undefined,
      serverUrl: "http://localhost:1337",
      webhookUrl: "https://smee.io/aBcD1234",
    });
  },
};

export const WithTheServerUrlConfigured: Story = {
  args: { status: { ...none, serverUrl: "https://graphnous.acme.test" } },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    await expect(canvas.queryByText("Server URL")).not.toBeInTheDocument();
    await expect(canvas.getByRole("button", { name: "Create on GitHub" })).toBeEnabled();
  },
};

export const Blocked: Story = {
  args: { status: { ...none, blocked: "Set a secrets key (graphnous.secrets.key) to keep the App's secrets" } },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    await expect(canvas.getByText("Set a secrets key (graphnous.secrets.key) to keep the App's secrets")).toBeInTheDocument();
    await expect(canvas.getByRole("button", { name: "Create on GitHub" })).toBeDisabled();
  },
};

export const FailingToStart: Story = {
  args: {
    status: { ...none, serverUrl: "https://graphnous.acme.test" },
    onRegister: fn(async () => {
      throw new Error("Not a GitHub organization: acme/../evil");
    }),
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    await userEvent.click(canvas.getByRole("button", { name: "Create on GitHub" }));

    await expect(await canvas.findByText("Not a GitHub organization: acme/../evil")).toBeInTheDocument();
  },
};

export const Connected: Story = {
  args: { status: connected },
  play: async ({ args, canvasElement }) => {
    const canvas = within(canvasElement);

    await expect(canvas.getByText("Graphnous Acme")).toBeInTheDocument();
    await expect(canvas.getByText("Connected")).toBeInTheDocument();
    await expect(canvas.getByRole("link", { name: /Install it on repositories/ }))
      .toHaveAttribute("href", "https://github.com/apps/graphnous-acme/installations/new");

    await userEvent.click(canvas.getByRole("button", { name: "Disconnect" }));

    await expect(args.onDisconnect).toHaveBeenCalled();
  },
};

export const FromConfiguration: Story = {
  args: { status: { ...connected, source: "CONFIGURATION" } },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    await expect(canvas.getByText("From configuration")).toBeInTheDocument();
    // The configuration names it, so it is not disconnected here
    await expect(canvas.queryByRole("button", { name: "Disconnect" })).not.toBeInTheDocument();
  },
};

export const NotAnswering: Story = {
  args: { status: { ...connected, error: "GitHub did not answer as the App: 401 Unauthorized" } },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    await expect(canvas.getByText("Not answering")).toBeInTheDocument();
    await expect(canvas.getByText("GitHub did not answer as the App: 401 Unauthorized")).toBeInTheDocument();
  },
};

export const Off: Story = {
  args: { status: undefined },
  play: async ({ canvasElement }) => {
    await expect(within(canvasElement).getByText(/has the GitHub App off/)).toBeInTheDocument();
  },
};
