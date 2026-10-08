"use client";

import {
    Alert,
    Badge,
    Code,
    EmptyState,
    Field,
    IconButton,
    Spinner,
    Tab,
    Table,
    TabList,
    TabPanel,
    Tabs,
    Text,
} from "@graphnous/theme";
import { ArrowsLeftRightIcon, GitDiffIcon } from "@phosphor-icons/react";
import { useState } from "react";

import { NODE_TYPES } from "@/components/ScanGraphCanvas/graphStyle";
import { completedScans, ScanSelector } from "@/components/ScanSelector/ScanSelector";
import { useCompareScans } from "@/lib/hooks/scan/comparison/useCompareScans";
import type {
    ChangedNode,
    ComparedNode,
    ComparedNodeType,
    Scan,
    ScanComparison as Comparison,
    ScanComparisonSummary,
} from "@/types";

/**
 * A type of compared node, as the graph's legend calls it.
 */
function TypeLabel({ type }: { type: ComparedNodeType }) {
    const style = NODE_TYPES[type];

    return (
        <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
            <span
                className="size-2.5 shrink-0 rounded-full"
                style={{ backgroundColor: `var(${style?.token ?? "--color-foreground-muted"})` }}
                aria-hidden
            />
            {style?.label ?? type}
        </span>
    );
}

/**
 * A property's value as text: lists joined, nothing as a dash.
 */
export function formatValue(value: unknown): string {
    if (value === null || value === undefined) {
        return "—";
    }

    if (Array.isArray(value)) {
        return value.length === 0 ? "none" : value.map(formatValue).join(", ");
    }

    if (typeof value === "object") {
        return JSON.stringify(value);
    }

    return String(value);
}

function NodeName({ node }: { node: ComparedNode | ChangedNode }) {
    return (
        <div className="min-w-0">
            <div className="font-medium">{node.name ?? node.key}</div>
            <Text as="div" size="xs" tone="muted" truncate>
                <span title={node.key}>{node.key}</span>
            </Text>
        </div>
    );
}

/**
 * How many nodes of each type the head scan added, removed and changed.
 */
export function ScanComparisonSummaryTable({ summary }: { summary: ScanComparisonSummary[] }) {
    return (
        <Table
            caption="Changes by type"
            rows={summary}
            rowKey={(row) => row.type}
            columns={[
                { key: "type", header: "Type", cell: (row) => <TypeLabel type={row.type} /> },
                {
                    key: "added",
                    header: "Added",
                    align: "end",
                    cell: (row) => (row.added ? <Badge tone="success">+{row.added}</Badge> : "—"),
                },
                {
                    key: "removed",
                    header: "Removed",
                    align: "end",
                    cell: (row) => (row.removed ? <Badge tone="error">−{row.removed}</Badge> : "—"),
                },
                {
                    key: "changed",
                    header: "Changed",
                    align: "end",
                    cell: (row) => (row.changed ? <Badge tone="warning">~{row.changed}</Badge> : "—"),
                },
            ]}
        />
    );
}

function NodesTable({ caption, nodes }: { caption: string; nodes: ComparedNode[] }) {
    return (
        <Table
            caption={caption}
            rows={nodes}
            rowKey={(node) => `${node.type}|${node.key}`}
            empty={<Text size="sm" tone="muted">None.</Text>}
            columns={[
                { key: "type", header: "Type", className: "w-32", cell: (node) => <TypeLabel type={node.type} /> },
                { key: "name", header: "Name", cell: (node) => <NodeName node={node} /> },
            ]}
        />
    );
}

function ChangedTable({ nodes }: { nodes: ChangedNode[] }) {
    return (
        <Table
            caption="Changed"
            rows={nodes}
            rowKey={(node) => `${node.type}|${node.key}`}
            empty={<Text size="sm" tone="muted">None.</Text>}
            columns={[
                { key: "type", header: "Type", className: "w-32", cell: (node) => <TypeLabel type={node.type} /> },
                { key: "name", header: "Name", cell: (node) => <NodeName node={node} /> },
                {
                    key: "changes",
                    header: "What changed",
                    cell: (node) => (
                        <ul className="space-y-1">
                            {node.properties.map((property) => (
                                <li key={property.name} className="text-sm">
                                    <Code className="text-xs">{property.name}</Code>{" "}
                                    <del className="text-foreground-muted">{formatValue(property.before)}</del>
                                    {" → "}
                                    <ins className="no-underline">{formatValue(property.after)}</ins>
                                </li>
                            ))}
                        </ul>
                    ),
                },
            ]}
        />
    );
}

export type ScanComparisonViewProps = {
    comparison: Comparison;
};

/**
 * How one scan's graph differs from another's: the counts by type, then
 * the nodes added, removed and changed, each on a tab.
 */
export function ScanComparisonView({ comparison }: ScanComparisonViewProps) {
    if (comparison.summary.length === 0) {
        return (
            <EmptyState
                icon={GitDiffIcon}
                title="No differences"
                description="Both scans found the same targets, modules, packages, files, classes, methods, fields and dependencies."
            />
        );
    }

    return (
        <div className="space-y-4">
            <ScanComparisonSummaryTable summary={comparison.summary} />

            {comparison.truncated && (
                <Alert tone="warning">
                    There are more changes than fit: each list shows the first 500, from targets down to fields.
                </Alert>
            )}

            <Tabs defaultValue="added">
                <TabList label="Changes">
                    <Tab value="added">Added ({total(comparison.summary, "added")})</Tab>
                    <Tab value="removed">Removed ({total(comparison.summary, "removed")})</Tab>
                    <Tab value="changed">Changed ({total(comparison.summary, "changed")})</Tab>
                </TabList>
                <TabPanel value="added" className="pt-4">
                    <NodesTable caption="Added" nodes={comparison.added} />
                </TabPanel>
                <TabPanel value="removed" className="pt-4">
                    <NodesTable caption="Removed" nodes={comparison.removed} />
                </TabPanel>
                <TabPanel value="changed" className="pt-4">
                    <ChangedTable nodes={comparison.changed} />
                </TabPanel>
            </Tabs>
        </div>
    );
}

function total(summary: ScanComparisonSummary[], change: "added" | "removed" | "changed"): number {
    return summary.reduce((sum, row) => sum + row[change], 0);
}

export type ProjectScanComparisonProps = {
    /**
     * The project's scans; two of the completed ones are compared, the
     * newest with the one before it at first.
     */
    scans: Scan[];
};

/**
 * Comparing two of a project's scans: what the later one, the head, found
 * that the earlier one, the base, did not, and the other way around.
 */
export function ProjectScanComparison({ scans }: ProjectScanComparisonProps) {
    const completed = completedScans(scans);

    const [chosenBase, setChosenBase] = useState<string | null>(null);
    const [chosenHead, setChosenHead] = useState<string | null>(null);

    const head = chosenHead ?? completed[0]?.id ?? null;
    const base = chosenBase ?? completed[1]?.id ?? null;

    const { comparison, loading, error } = useCompareScans(base, head);

    const swap = () => {
        setChosenBase(head);
        setChosenHead(base);
    };

    if (completed.length < 2) {
        return (
            <EmptyState
                icon={GitDiffIcon}
                title="Not enough completed scans yet"
                description="Comparing shows what changed between two completed scans of the project. Scan it again once its first scan completes."
            />
        );
    }

    return (
        <div className="space-y-4 pt-4">
            <div className="flex flex-wrap items-end gap-3">
                <div className="w-72">
                    <Field label="Base scan">
                        <ScanSelector scans={scans} value={base} onScanChange={(scan) => setChosenBase(scan.id)} />
                    </Field>
                </div>
                <IconButton icon={ArrowsLeftRightIcon} label="Swap the scans" variant="ghost" onClick={swap} />
                <div className="w-72">
                    <Field label="Head scan">
                        <ScanSelector scans={scans} value={head} onScanChange={(scan) => setChosenHead(scan.id)} />
                    </Field>
                </div>
                {loading && <Spinner size="sm" label="Comparing the scans" />}
            </div>

            {base === head ? (
                <Text size="sm" tone="muted">Choose two different scans to compare.</Text>
            ) : error ? (
                <Alert tone="error" title="The scans could not be compared" announce>
                    {error.message}
                </Alert>
            ) : (
                comparison && <ScanComparisonView comparison={comparison} />
            )}
        </div>
    );
}

/**
 * What the project's newest completed scan changed since the one before
 * it, by type.
 */
export function LatestScanChanges({ scans }: ProjectScanComparisonProps) {
    const completed = completedScans(scans);
    const { comparison, error } = useCompareScans(completed[1]?.id ?? null, completed[0]?.id ?? null);

    if (completed.length < 2) {
        return <Text size="sm" tone="muted">There is no earlier completed scan to compare with yet.</Text>;
    }

    if (error) {
        return <Text size="sm" tone="muted">{error.message}</Text>;
    }

    if (!comparison) {
        return <Spinner size="sm" label="Comparing the scans" />;
    }

    return comparison.summary.length === 0
        ? <Text size="sm" tone="muted">No differences.</Text>
        : <ScanComparisonSummaryTable summary={comparison.summary} />;
}
