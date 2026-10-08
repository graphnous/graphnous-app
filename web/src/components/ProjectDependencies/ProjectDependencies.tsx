"use client";

import {
    Alert,
    Badge,
    Card,
    CardBody,
    CardHeader,
    Code,
    EmptyState,
    Field,
    Pagination,
    SearchInput,
    Table,
    Text,
    type Sort,
} from "@graphnous/theme";
import { PackageIcon } from "@phosphor-icons/react";
import { useState } from "react";

import { formatValue } from "@/components/ScanComparison/ScanComparison";
import { completedScans, ScanSelector } from "@/components/ScanSelector/ScanSelector";
import { useCompareScans } from "@/lib/hooks/scan/comparison/useCompareScans";
import { useDependencies } from "@/lib/hooks/scan/dependencies/useDependencies";
import type { Page, Scan, ScanComparison, ScanDependency, ScanDependencySort } from "@/types";

const PAGE_SIZES = [25, 50, 100];

export type DependenciesTableProps = {
    page: Page<ScanDependency>;
    sort: Sort<ScanDependencySort>;
    onSortChange: (sort: Sort<ScanDependencySort>) => void;
    loading?: boolean;
    /**
     * Whether a search narrows the list, for what to say when it is empty.
     */
    searching?: boolean;
};

/**
 * A page of a scan's dependencies, sortable by library, scope and module.
 */
export function DependenciesTable({ page, sort, onSortChange, loading, searching }: DependenciesTableProps) {
    return (
        <Table<ScanDependency, ScanDependencySort>
            caption="Dependencies"
            rows={page.content}
            rowKey={(dependency) => `${dependency.module}|${dependency.name}`}
            sort={sort}
            onSortChange={onSortChange}
            loading={loading}
            empty={
                <EmptyState
                    icon={PackageIcon}
                    title={searching ? "No dependencies match" : "No dependencies"}
                    description={
                        searching
                            ? "No library the scan found has a name with this in it."
                            : "The scan found no libraries its modules depend on."
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
                    key: "module",
                    header: "Module",
                    sortable: true,
                    cell: (dependency) => <span className="font-mono text-sm">{dependency.module}</span>,
                },
            ]}
        />
    );
}

export type DependencyChangesProps = {
    /**
     * The scan's comparison with the one before it.
     */
    comparison: ScanComparison;
};

/**
 * The libraries a scan added, removed and changed, such as to another
 * version, since the scan before it.
 */
export function DependencyChanges({ comparison }: DependencyChangesProps) {
    const added = comparison.added.filter((node) => node.type === "Dependency");
    const removed = comparison.removed.filter((node) => node.type === "Dependency");
    const changed = comparison.changed.filter((node) => node.type === "Dependency");

    if (added.length + removed.length + changed.length === 0) {
        return <Text size="sm" tone="muted">The same libraries as the scan before it.</Text>;
    }

    const version = (properties: Record<string, unknown>) =>
        typeof properties.version === "string" ? ` ${properties.version}` : "";

    return (
        <ul className="space-y-2 text-sm">
            {added.map((node) => (
                <li key={node.key} className="flex items-start gap-2">
                    <Badge tone="success">Added</Badge>
                    <span className="break-all font-mono">{node.name}{version(node.properties)}</span>
                </li>
            ))}
            {removed.map((node) => (
                <li key={node.key} className="flex items-start gap-2">
                    <Badge tone="error">Removed</Badge>
                    <span className="break-all font-mono">{node.name}{version(node.properties)}</span>
                </li>
            ))}
            {changed.map((node) => (
                <li key={node.key} className="flex items-start gap-2">
                    <Badge tone="warning">Changed</Badge>
                    <span className="min-w-0">
                        <span className="break-all font-mono">{node.name}</span>
                        {node.properties.map((property) => (
                            <span key={property.name} className="block text-foreground-secondary">
                                {property.name} {formatValue(property.before)} → {formatValue(property.after)}
                            </span>
                        ))}
                    </span>
                </li>
            ))}
        </ul>
    );
}

export type ProjectDependenciesProps = {
    /**
     * The project's scans; the dependencies are those of one of the
     * completed ones, the newest at first.
     */
    scans: Scan[];
};

/**
 * The libraries a project's scan found its modules depend on, searchable
 * and sortable, with what changed since the scan before it.
 */
export function ProjectDependencies({ scans }: ProjectDependenciesProps) {
    const completed = completedScans(scans);

    const [chosenScanId, setChosenScanId] = useState<string | null>(null);
    const scanId = chosenScanId ?? completed[0]?.id ?? null;

    // The completed scan before the chosen one, to compare with
    const previousId = completed[completed.findIndex((scan) => scan.id === scanId) + 1]?.id ?? null;

    const [query, setQuery] = useState("");
    const [sort, setSort] = useState<Sort<ScanDependencySort>>({ field: "name", direction: "asc" });
    const [pageNumber, setPageNumber] = useState(0);
    const [size, setSize] = useState(50);

    const { page, loading, error } = useDependencies({
        scanId,
        query,
        page: pageNumber,
        size,
        sort: sort.field,
        direction: sort.direction,
    });

    const { comparison, error: comparisonError } = useCompareScans(previousId, scanId);

    if (!scanId) {
        return (
            <EmptyState
                icon={PackageIcon}
                title="No completed scans yet"
                description="The dependencies are what a scan found. Scan the project, and once the scan completes, its dependencies show here."
            />
        );
    }

    return (
        <div className="space-y-4 pt-4">
            <div className="flex flex-wrap items-end gap-3">
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
                <div className="ml-auto w-72">
                    <Field label="Scan">
                        <ScanSelector
                            scans={scans}
                            value={scanId}
                            onScanChange={(scan) => {
                                setChosenScanId(scan.id);
                                setPageNumber(0);
                            }}
                        />
                    </Field>
                </div>
            </div>

            {error && (
                <Alert tone="error" title="The dependencies could not be loaded" announce>
                    {error.message}
                </Alert>
            )}

            <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
                <div className="min-w-0 flex-[2] space-y-3">
                    <DependenciesTable
                        page={page}
                        sort={sort}
                        onSortChange={(next) => {
                            setSort(next);
                            setPageNumber(0);
                        }}
                        loading={loading}
                        searching={query.trim() !== ""}
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

                <Card className="flex-1">
                    <CardHeader title="Changes since previous scan" description="The libraries added, removed and changed" />
                    <CardBody>
                        {!previousId ? (
                            <Text size="sm" tone="muted">There is no earlier completed scan to compare with.</Text>
                        ) : comparisonError ? (
                            <Text size="sm" tone="muted">{comparisonError.message}</Text>
                        ) : comparison ? (
                            <DependencyChanges comparison={comparison} />
                        ) : (
                            <Text size="sm" tone="muted">Comparing…</Text>
                        )}
                    </CardBody>
                </Card>
            </div>
        </div>
    );
}
