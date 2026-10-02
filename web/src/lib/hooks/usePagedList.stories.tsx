import type { Meta, StoryObj } from "@storybook/nextjs-vite";
import { getRouter } from "@storybook/nextjs-vite/navigation.mock";
import { expect, userEvent } from "storybook/test";
import { Pagination, Table, type Column } from "graphnous-theme";

import { usePagedList } from "./usePagedList";

type Project = { id: string; name: string; createdAt: string };

const columns: Column<Project>[] = [
  { key: "name", header: "Name", sortable: true, cell: (project) => project.name },
  { key: "createdAt", header: "Created", sortable: true, cell: (project) => project.createdAt },
];

/**
 * A list of projects whose page and sort are in the URL; the rows are made
 * up, the API would return them.
 */
function Projects({ prefix }: { prefix?: string }) {
  const list = usePagedList({ sortFields: ["name", "createdAt"], defaultSort: "name", prefix });
  const totalElements = 132;

  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm">
        Requesting page={list.page} size={list.size} sort={list.sort} direction={list.direction}
      </p>
      <Table
        caption="Projects"
        columns={columns}
        rows={[{ id: "1", name: `Project ${list.page * list.size + 1}`, createdAt: "2026-09-30" }]}
        rowKey={(project) => project.id}
        sort={{ field: list.sort, direction: list.direction }}
        onSortChange={list.setSort}
      />
      <Pagination
        page={list.page}
        size={list.size}
        totalElements={totalElements}
        totalPages={Math.ceil(totalElements / list.size)}
        onPageChange={list.setPage}
        onSizeChange={list.setSize}
      />
    </div>
  );
}

const navigation = (query: Record<string, string>) => ({
  nextjs: { appDirectory: true, navigation: { pathname: "/projects", query } },
});

const meta = {
  title: "Patterns/usePagedList",
  component: Projects,
  parameters: navigation({}),
} satisfies Meta<typeof Projects>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Defaults: Story = {
  play: async ({ canvas }) => {
    await expect(canvas.getByText("Requesting page=0 size=20 sort=name direction=asc")).toBeVisible();

    await userEvent.click(canvas.getByRole("button", { name: "Next page" }));
    // Pages count from 1 in the URL; defaults are left out
    await expect(getRouter().push).toHaveBeenLastCalledWith("/projects?page=2", { scroll: false });
  },
};

export const FromTheUrl: Story = {
  parameters: navigation({ page: "3", size: "50", sort: "createdAt", direction: "desc", q: "graph" }),
  play: async ({ canvas }) => {
    await expect(canvas.getByText("Requesting page=2 size=50 sort=createdAt direction=desc")).toBeVisible();

    // Sorting goes back to the first page, and keeps other parameters
    await userEvent.click(canvas.getByRole("button", { name: "Name" }));
    await expect(getRouter().push).toHaveBeenLastCalledWith("/projects?size=50&q=graph", { scroll: false });
  },
};

export const SizeChanged: Story = {
  parameters: navigation({ page: "4" }),
  play: async ({ canvas }) => {
    await userEvent.selectOptions(canvas.getByRole("combobox", { name: "Rows per page" }), "100");
    await expect(getRouter().push).toHaveBeenLastCalledWith("/projects?size=100", { scroll: false });
  },
};

export const InvalidValues: Story = {
  parameters: navigation({ page: "-2", size: "1000", sort: "password", direction: "sideways" }),
  play: async ({ canvas }) => {
    await expect(canvas.getByText("Requesting page=0 size=20 sort=name direction=asc")).toBeVisible();
  },
};

export const Prefixed: Story = {
  args: { prefix: "scans" },
  parameters: navigation({ scansPage: "2", page: "9" }),
  play: async ({ canvas }) => {
    await expect(canvas.getByText("Requesting page=1 size=20 sort=name direction=asc")).toBeVisible();

    await userEvent.click(canvas.getByRole("button", { name: "Next page" }));
    await expect(getRouter().push).toHaveBeenLastCalledWith("/projects?scansPage=3&page=9", { scroll: false });
  },
};
