import { useEffect, useState } from "react";
import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { expect, fn } from "storybook/test";

import type { ProjectPage } from "@/generated/api";

import { ApiClientProvider, useApi } from "./client";

const page: ProjectPage = {
  content: [
    {
      id: "6f1c",
      name: "Graphnous",
      gitUrl: "https://github.com/graphnous/graphnous.git",
      systemId: "a1",
      createdAt: new Date("2026-09-01T10:00:00Z"),
      updatedAt: new Date("2026-09-30T18:41:07Z"),
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

const fetchApi = fn(async () => new Response(JSON.stringify(page), { headers: { "Content-Type": "application/json" } }));

function Projects() {
  const api = useApi();
  const [names, setNames] = useState<string[]>();

  useEffect(() => {
    api.projects
      .listProjects({ systemId: "a1", page: 0, size: 20, sort: "name", direction: "asc" })
      .then((result) => setNames(result.content.map((project) => project.name)));
  }, [api]);

  return <ul aria-label="Projects">{names?.map((name) => <li key={name}>{name}</li>)}</ul>;
}

const meta = {
  title: "Patterns/ApiClientProvider",
  component: ApiClientProvider,
  args: { baseUrl: "http://localhost:8080", fetchApi, children: <Projects /> },
} satisfies Meta<typeof ApiClientProvider>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvas }) => {
    await expect(await canvas.findByRole("listitem")).toHaveTextContent("Graphnous");

    const [url] = fetchApi.mock.lastCall as unknown as [string];
    await expect(url).toBe("http://localhost:8080/api/v1/systems/a1/projects?page=0&size=20&sort=name&direction=asc");
  },
};
