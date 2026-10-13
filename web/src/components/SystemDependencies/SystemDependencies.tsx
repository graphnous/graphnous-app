"use client";

import {
    Alert,
    Badge,
    Code,
    EmptyState,
    Link,
    Pagination,
    SearchInput,
    Table,
    type Sort,
} from "@graphnous/theme";
import { PackageIcon } from "@phosphor-icons/react";
import { useState } from "react";

import { useSystemDependencies } from "@/lib/hooks/system/useSystemDependencies";
import type { Project, ScanDependencySort, SystemDependency } from "@/types";

const PAGE_SIZES = [25, 50, 100];

export type SystemDependenciesProps = {
    systemId: string;
    /**
     * The projects of the system, to call each dependency's project by name.
     */
    projects: Project[];
};

/**
 * The libraries the projects of a system depend on, as the latest completed
 * scan of each found them, searchable and sortable.
 */
export function SystemDependencies({ systemId, projects }: SystemDependenciesProps) {
    const [query, setQuery] = useState("");
    const [sort, setSort] = useState<Sort<ScanDependencySort>>({ field: "name", direction: "asc" });
    const [pageNumber, setPageNumber] = useState(0);
    const [size, setSize] = useState(50);

    const { page, loading, error } = useSystemDependencies({
        systemId,
        query,
        page: pageNumber,
        size,
        sort: sort.field,
        direction: sort.direction,
    });

    const searching = query.trim() !== "";
    const projectName = (id: string) => projects.find((project) => project.id === id)?.name ?? id;

    return (
        <div className="space-y-4">
            <div className="w-72">
                <SearchInput
                    label="Search libraries"
                    placeholder="Search libraries"
                    defaultValue={query}
                    onSearch={(value) => {
                        setQuery(value);
                        setPageNumber(0);
                    }}
                />
            </div>

            {error && (
                <Alert tone="error" title="The dependencies could not be loaded" announce>
                    {error.message}
                </Alert>
            )}

            <Table<SystemDependency, ScanDependencySort>
                caption="Dependencies"
                rows={page.content}
                rowKey={(dependency) => `${dependency.scanId}|${dependency.module}|${dependency.name}`}
                sort={sort}
                onSortChange={(next) => {
                    setSort(next);
                    setPageNumber(0);
                }}
                loading={loading}
                empty={
                    <EmptyState
                        icon={PackageIcon}
                        title={searching ? "No dependencies match" : "No dependencies"}
                        description={
                            searching
                                ? "No library the system's projects depend on has a name with this in it."
                                : "The dependencies are what scans found. Once a project's scan completes, the libraries its modules depend on show here."
                        }
                    />
                }
                columns={[
                    {
                        key: "name",
                        header: "Library",
                        sortable: true,
                        cell: (dependency) => <span className="break-all font-mono text-sm">{dependency.name}</span>,
                    },
                    {
                        key: "version",
                        header: "Version",
                        cell: (dependency) => (dependency.version ? <Code className="text-xs">{dependency.version}</Code> : "—"),
                    },
                    {
                        key: "scope",
                        header: "Scope",
                        sortable: true,
                        cell: (dependency) => (dependency.scope ? <Badge>{dependency.scope}</Badge> : "—"),
                    },
                    {
                        key: "project",
                        header: "Project",
                        cell: (dependency) => (
                            <Link href={`/projects/${dependency.projectId}`}>{projectName(dependency.projectId)}</Link>
                        ),
                    },
                    {
                        key: "module",
                        header: "Module",
                        sortable: true,
                        cell: (dependency) => <span className="font-mono text-sm">{dependency.module}</span>,
                    },
                ]}
            />

            {page.totalElements > 0 && (
                <Pagination
                    page={page.page}
                    size={size}
                    totalElements={page.totalElements}
                    totalPages={page.totalPages}
                    sizes={PAGE_SIZES}
                    onPageChange={setPageNumber}
                    onSizeChange={(next) => {
                        setSize(next);
                        setPageNumber(0);
                    }}
                />
            )}
        </div>
    );
}
