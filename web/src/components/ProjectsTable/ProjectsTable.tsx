"use client";

import {
    Column,
    ConfirmDialog,
    EmptyState,
    IconButton,
    Link,
    Menu,
    Sort,
    Table,
    useToast,
} from "@graphnous/theme";
import { DotsThreeIcon, TrashIcon } from "@phosphor-icons/react";

import { useDeleteProject } from "@/lib/hooks/project/useDeleteProject";
import type { Page, Project } from "@/types";
import { useState } from "react";

export type ProjectsTableProps = {
    page: Page<Project>;
    /**
     * Called after a project was deleted, such as to load the projects again.
     */
    onProjectDeleted?: (project: Project) => void;
};

/**
 * The projects of a system, with the actions on each in its last column.
 */
export function ProjectsTable({
    page,
    onProjectDeleted,
}: ProjectsTableProps) {
    const [sort, setSort] = useState<Sort>({
        field: 'createdAt',
        direction: 'asc'
    });

    // The project whose delete is being confirmed
    const [deleting, setDeleting] = useState<Project | null>(null);

    const { deleteProject, loading, error, reset } = useDeleteProject();
    const { toast } = useToast();

    if (!page || !page.content) {
        return <EmptyState title={"Projects"} />
    }

    const closeDelete = () => {
        setDeleting(null);
        reset();
    };

    const confirmDelete = async () => {
        if (!deleting) {
            return;
        }

        try {
            await deleteProject(deleting.id);
        } catch {
            // Shown in the dialog, through the hook's error
            return;
        }

        toast({ title: `Project ${deleting.name} deleted`, tone: "success" });
        onProjectDeleted?.(deleting);
        closeDelete();
    };

    const columns: Column<Project>[] = [
        {
            key: 'name',
            header: 'Name',
            sortable: true,
            cell: (project) => <Link href={`/projects/${project.id}`}>{project.name}</Link>
        },
        {
            key: 'repository',
            header: 'Repository',
            cell: (project) => <span className="font-medium">
                {project.gitUrl}
            </span>
        },
        {
            key: 'actions',
            header: 'Actions',
            cell: (project) => (
                <Menu
                    trigger={
                        <IconButton
                            icon={DotsThreeIcon}
                            label={`Actions for ${project.name}`}
                            variant="ghost"
                            size="sm"
                        />
                    }
                    items={[
                        {
                            label: "Delete project",
                            icon: TrashIcon,
                            danger: true,
                            onSelect: () => setDeleting(project),
                        },
                    ]}
                />
            )
        }
    ]

    const rows = page.content.sort((a, b) => {
        const field = sort.field as 'name' | 'gitUrl';
        const order = a[field] < b[field] ? -1 : a[field] > b[field] ? 1 : 0;
        return sort.direction === 'asc' ? order : -order;
    });

    return (
        <>
            <Table
                rows={rows}
                sort={sort}
                columns={columns}
                caption={"Projects"}
                rowKey={(p) => p.id}
                onSortChange={(next) => {
                    setSort(next);
                }}>

            </Table>

            <ConfirmDialog
                open={deleting !== null}
                onClose={closeDelete}
                onConfirm={confirmDelete}
                title={`Delete ${deleting?.name ?? "project"}?`}
                description="The project is deleted with all of its scans, their results in the graph and their logs. This cannot be undone."
                confirmLabel="Delete project"
                danger
                confirming={loading}
                error={error?.message}
            />
        </>
    );
}
