"use client";

import { Alert, Button, EmptyState, Field, Select, Spinner, Text } from "@graphnous/theme";
import { ArrowCounterClockwiseIcon, ArrowLeftIcon, GraphIcon } from "@phosphor-icons/react";
import dynamic from "next/dynamic";
import { useState } from "react";

import { NodeDetails } from "@/components/ScanGraphCanvas/NodeDetails";
import { NODE_TYPES } from "@/components/ScanGraphCanvas/graphStyle";
import type { ScanGraphLayout } from "@/components/ScanGraphCanvas/ScanGraphCanvas";
import { completedScans, ScanSelector } from "@/components/ScanSelector/ScanSelector";
import { useGetScanGraph } from "@/lib/hooks/scan/graph/useGetScanGraph";
import type { Scan, ScanGraphNodeType } from "@/types";

// Draws on a canvas, so only in the browser
const ScanGraphCanvas = dynamic(
    () => import("@/components/ScanGraphCanvas/ScanGraphCanvas").then((module) => module.ScanGraphCanvas),
    { ssr: false },
);

const DEPTHS = [
    { value: "", label: "Default" },
    ...[1, 2, 3, 4, 5].map((depth) => ({ value: String(depth), label: depth === 1 ? "1 hop" : `${depth} hops` })),
];

const LAYOUTS = [
    { value: "rings", label: "Rings" },
    { value: "tree", label: "Tree" },
];

export type ProjectGraphProps = {
    /**
     * The project's scans; the graph is of one of the completed ones, the
     * newest at first.
     */
    scans: Scan[];
};

/**
 * Exploring the graph of a project's scan: around the scan at first, then
 * around whichever node is double-clicked or focused on, with the way
 * back. The scan to explore is chosen at the top right.
 */
export function ProjectGraph({ scans }: ProjectGraphProps) {
    const [chosenScanId, setChosenScanId] = useState<string | null>(null);
    const scanId = chosenScanId ?? completedScans(scans)[0]?.id ?? null;

    // The nodes focused on, in order, so the way back is known; the scan
    // itself while empty
    const [trail, setTrail] = useState<string[]>([]);
    const focus = trail.at(-1) ?? null;

    const [selected, setSelected] = useState<string | null>(null);
    const [depth, setDepth] = useState<number | null>(null);
    const [layout, setLayout] = useState<ScanGraphLayout>("rings");

    const { graph, loading, error } = useGetScanGraph({ scanId, focus, depth });

    const focusOn = (id: string) => {
        if (id === (graph?.focus ?? null)) {
            return;
        }

        setTrail((current) => [...current, id]);
        setSelected(id);
    };

    const back = () => {
        setTrail((current) => current.slice(0, -1));
        setSelected(null);
    };

    const reset = () => {
        setTrail([]);
        setSelected(null);
    };

    const chooseScan = (scan: Scan) => {
        setChosenScanId(scan.id);
        reset();
    };

    const selectedNode = graph?.nodes.find((node) => node.id === selected) ?? null;
    const focusNode = graph?.nodes.find((node) => node.id === graph.focus) ?? null;
    const types = new Set(graph?.nodes.map((node) => node.type));

    return (
        <div className="space-y-4 pt-4">
            <div className="flex flex-wrap items-end gap-3">
                <div className="flex min-w-0 flex-1 items-center gap-2">
                    <Button variant="ghost" size="sm" icon={ArrowLeftIcon} disabled={trail.length === 0} onClick={back}>
                        Back
                    </Button>
                    <Button
                        variant="ghost"
                        size="sm"
                        icon={ArrowCounterClockwiseIcon}
                        disabled={trail.length === 0}
                        onClick={reset}
                    >
                        Whole scan
                    </Button>
                    {focusNode && (
                        <Text as="span" size="sm" tone="secondary" truncate>
                            {focusNode.type === "Scan" ? (
                                "The whole scan"
                            ) : (
                                <>
                                    Around {NODE_TYPES[focusNode.type]?.label.toLowerCase() ?? focusNode.type}{" "}
                                    <span className="font-medium text-foreground">{focusNode.name}</span>
                                </>
                            )}
                        </Text>
                    )}
                </div>

                <div className="w-32">
                    <Field label="Depth">
                        <Select
                            options={DEPTHS}
                            value={depth === null ? "" : String(depth)}
                            onChange={(event) => setDepth(event.target.value === "" ? null : Number(event.target.value))}
                        />
                    </Field>
                </div>
                <div className="w-28">
                    <Field label="Layout">
                        <Select
                            options={LAYOUTS}
                            value={layout}
                            onChange={(event) => setLayout(event.target.value as ScanGraphLayout)}
                        />
                    </Field>
                </div>
                <div className="w-72">
                    <Field label="Scan">
                        <ScanSelector
                            scans={scans}
                            value={scanId}
                            onScanChange={chooseScan}
                            disabled={completedScans(scans).length === 0}
                        />
                    </Field>
                </div>
            </div>

            {!scanId ? (
                <EmptyState
                    icon={GraphIcon}
                    title="No completed scans yet"
                    description="The graph shows what a scan found. Scan the project, and once the scan completes, its graph shows here."
                />
            ) : (
                <>
                    {error && (
                        <Alert tone="error" title="The graph could not be loaded" announce>
                            {error.message}
                        </Alert>
                    )}

                    {graph?.truncated && (
                        <Alert tone="warning">
                            There are more nodes within reach than fit: the nearest {graph.nodes.length} are shown.
                            Focus on a node, or choose a smaller depth, to see the rest.
                        </Alert>
                    )}

                    <div className="flex flex-col gap-4 lg:flex-row">
                        <div className="relative h-[65vh] min-h-96 flex-1 overflow-hidden rounded-card border border-border bg-surface">
                            {graph && (
                                <ScanGraphCanvas
                                    graph={graph}
                                    layout={layout}
                                    selected={selected}
                                    onSelect={setSelected}
                                    onFocus={focusOn}
                                    // Cytoscape makes its container relative, so it is sized, not placed
                                    className="h-full w-full"
                                />
                            )}

                            {loading && (
                                <div className="absolute left-3 top-3 flex items-center gap-2 rounded-control bg-surface-elevated px-2 py-1 text-sm shadow-sm">
                                    <Spinner size="sm" label="Loading the graph" />
                                    <span>Loading…</span>
                                </div>
                            )}

                            {graph && graph.nodes.length <= 1 && !loading && (
                                <div className="absolute inset-x-0 bottom-6 text-center">
                                    <Text size="sm" tone="muted">The scan found nothing to show here.</Text>
                                </div>
                            )}
                        </div>

                        <aside className="w-full shrink-0 rounded-card border border-border bg-surface p-4 lg:w-80">
                            {selectedNode ? (
                                <NodeDetails
                                    node={selectedNode}
                                    isFocus={selectedNode.id === graph?.focus}
                                    onFocus={focusOn}
                                />
                            ) : (
                                <Text size="sm" tone="muted">
                                    Click a node to see what the scan found about it. Double-click it to focus the
                                    graph on it.
                                </Text>
                            )}
                        </aside>
                    </div>

                    {types.size > 0 && (
                        <ul className="flex flex-wrap gap-x-4 gap-y-1" aria-label="Legend">
                            {(Object.keys(NODE_TYPES) as ScanGraphNodeType[])
                                .filter((type) => types.has(type))
                                .map((type) => (
                                    <li key={type} className="flex items-center gap-1.5 text-xs text-foreground-secondary">
                                        <span
                                            className="size-2.5 rounded-full"
                                            style={{ backgroundColor: `var(${NODE_TYPES[type].token})` }}
                                            aria-hidden
                                        />
                                        {NODE_TYPES[type].label}
                                    </li>
                                ))}
                        </ul>
                    )}
                </>
            )}
        </div>
    );
}
