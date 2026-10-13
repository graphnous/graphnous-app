"use client";

import {
    Alert,
    Badge,
    Checkbox,
    Code,
    EmptyState,
    Link,
    Pagination,
    SearchInput,
    Table,
    Text,
    type Sort,
} from "@graphnous/theme";
import { PackageIcon } from "@phosphor-icons/react";
import { useState } from "react";

import { useSystemLibraries } from "@/lib/hooks/system/useSystemLibraries";
import type { Project, SystemLibrary, SystemLibrarySort } from "@/types";

const PAGE_SIZES = [25, 50, 100];

export type SystemLibrariesProps = {
    systemId: string;
    /**
     * The projects of the system, to call the projects of each version by
     * name.
     */
    projects: Project[];
};

/**
 * The libraries the projects of a system depend on, each once, with which
 * project uses which version: a library in more than one version has
 * drifted apart between them. Searchable, and narrowed down to the
 * libraries that drifted.
 */
export function SystemLibraries({ systemId, projects }: SystemLibrariesProps) {
    const [query, setQuery] = useState("");
    const [drift, setDrift] = useState(false);
    const [sort, setSort] = useState<Sort<SystemLibrarySort>>({ field: "name", direction: "asc" });
    const [pageNumber, setPageNumber] = useState(0);
    const [size, setSize] = useState(50);

    const { page, loading, error } = useSystemLibraries({
        systemId,
        query,
        drift,
        page: pageNumber,
        size,
        sort: sort.field,
        direction: sort.direction,
    });

    const narrowed = query.trim() !== "" || drift;
    const projectName = (id: string) => projects.find((project) => project.id === id)?.name ?? id;

    return (
        <div className="space-y-4 pt-4">
            <div className="flex flex-wrap items-center gap-4">
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
                <Checkbox
                    label="Only libraries in more than one version"
                    checked={drift}
                    onChange={() => {
                        setDrift(!drift);
                        setPageNumber(0);
                    }}
                />
            </div>

            {error && (
                <Alert tone="error" title="The libraries could not be loaded" announce>
                    {error.message}
                </Alert>
            )}

            <Table<SystemLibrary, SystemLibrarySort>
                caption="Libraries"
                rows={page.content}
                rowKey={(library) => library.name}
                sort={sort}
                onSortChange={(next) => {
                    setSort(next);
                    setPageNumber(0);
                }}
                loading={loading}
                empty={
                    <EmptyState
                        icon={PackageIcon}
                        title={narrowed ? "No libraries match" : "No libraries"}
                        description={
                            drift && query.trim() === ""
                                ? "Every library the system's projects depend on is used in one version."
                                : narrowed
                                    ? "No library the system's projects depend on matches."
                                    : "The libraries are what scans found. Once a project's scan completes, the libraries its modules depend on show here."
                        }
                    />
                }
                columns={[
                    {
                        key: "name",
                        header: "Library",
                        sortable: true,
                        cell: (library) => <span className="break-all font-mono text-sm">{library.name}</span>,
                    },
                    {
                        key: "versions",
                        header: "Versions",
                        sortable: true,
                        cell: (library) => (
                            library.versions.length > 1
                                ? <Badge tone="warning">{library.versions.length} versions</Badge>
                                : <Text as="span" size="sm" tone="muted">1 version</Text>
                        ),
                    },
                    {
                        key: "usage",
                        header: "Used by",
                        cell: (library) => (
                            <ul className="space-y-1">
                                {library.versions.map((version) => (
                                    <li key={version.version ?? ""} className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
                                        {version.version
                                            ? <Code className="text-xs">{version.version}</Code>
                                            : <Text as="span" size="sm" tone="muted">no version</Text>}
                                        <span className="flex flex-wrap gap-x-2 text-sm">
                                            {version.projects.map((projectId) => (
                                                <Link key={projectId} href={`/projects/${projectId}`}>
                                                    {projectName(projectId)}
                                                </Link>
                                            ))}
                                        </span>
                                    </li>
                                ))}
                            </ul>
                        ),
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
