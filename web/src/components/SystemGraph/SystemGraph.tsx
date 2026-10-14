"use client";

import { Alert, Button, Checkbox, EmptyState, Field, Select, Spinner, Text } from "@graphnous/theme";
import { ArrowCounterClockwiseIcon, ArrowLeftIcon, GraphIcon } from "@phosphor-icons/react";
import dynamic from "next/dynamic";
import { useMemo, useState } from "react";

import { LayerFilter } from "@/components/LayerFilter/LayerFilter";
import { SystemLinks } from "@/components/SystemLinks/SystemLinks";
import { NodeTypeFilter, nodeTypes, withoutTypes } from "@/components/NodeTypeFilter/NodeTypeFilter";
import { NodeDetails } from "@/components/ScanGraphCanvas/NodeDetails";
import { NODE_TYPES } from "@/components/ScanGraphCanvas/graphStyle";
import type { ScanGraphLayout } from "@/components/ScanGraphCanvas/ScanGraphCanvas";
import { completedScans, ScanSelector } from "@/components/ScanSelector/ScanSelector";
import { useGetScanGraph } from "@/lib/hooks/scan/graph/useGetScanGraph";
import { chosenLayers, tickedLayers } from "@/lib/graphLayers";
import { useSystemLinks } from "@/lib/hooks/system/useSystemLinks";
import { useGetScanGraphs } from "@/lib/hooks/scan/graph/useGetScanGraphs";
import type { Project, Scan, ScanGraph, ScanGraphEdge, ScanGraphLayer, ScanGraphNodeType, SystemLink } from "@/types";

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

export type SystemGraphProps = {
    /**
     * The system the projects are of, to find how they are linked.
     */
    systemId?: string | null;
    /**
     * The projects of the system.
     */
    projects: Project[];
    /**
     * The scans of those projects; the graph is of the completed ones.
     */
    scans: Scan[];
};

/**
 * A node of one scan's graph, focused on.
 */
type Focus = {
    scanId: string;
    nodeId: string;
};

/**
 * The id of a scan's node in the graph of several scans: scans may use the
 * same id for nodes of their own.
 */
function systemNodeId(scanId: string, nodeId: string): string {
    return `${scanId}:${nodeId}`;
}

/**
 * The graphs of several scans as one, each scan called by its project.
 */
export function mergeGraphs(graphs: ScanGraph[], projectNames: Record<string, string>): ScanGraph | null {
    if (graphs.length === 0) {
        return null;
    }

    return {
        scanId: graphs[0].scanId,
        focus: systemNodeId(graphs[0].scanId, graphs[0].focus),
        depth: Math.max(...graphs.map((graph) => graph.depth)),
        nodes: graphs.flatMap((graph) => graph.nodes.map((node) => ({
            ...node,
            id: systemNodeId(graph.scanId, node.id),
            name: node.type === "Scan" ? projectNames[graph.scanId] ?? node.name : node.name,
        }))),
        edges: graphs.flatMap((graph) => graph.edges.map((edge) => ({
            ...edge,
            source: systemNodeId(graph.scanId, edge.source),
            target: systemNodeId(graph.scanId, edge.target),
        }))),
        truncated: graphs.some((graph) => graph.truncated),
        layers: mergeLayers(graphs),
    };
}

/**
 * The graph with how its scans are linked over HTTP drawn in: a
 * CALLS_ENDPOINTS_OF relationship from the scan of each project to the scan of each other
 * project it requests endpoints of, saying how many, and a REQUESTS
 * relationship from each call to its endpoint when the graph has both.
 */
export function withLinks(graph: ScanGraph | null, links: SystemLink[]): ScanGraph | null {
    if (!graph || links.length === 0) {
        return graph;
    }

    const nodes = new Set(graph.nodes.map((node) => node.id));
    const edges: ScanGraphEdge[] = [];

    // The endpoints requested, per pair of scans
    const between = new Map<string, { source: string; target: string; endpoints: Set<string> }>();

    for (const { call, endpoint, crossesProjects } of links) {
        const source = systemNodeId(call.scanId, call.id);
        const target = systemNodeId(endpoint.scanId, endpoint.id);

        if (nodes.has(source) && nodes.has(target)) {
            edges.push({ source, target, type: "REQUESTS", properties: {} });
        }

        // The scan's own node has the scan's id
        const from = systemNodeId(call.scanId, call.scanId);
        const to = systemNodeId(endpoint.scanId, endpoint.scanId);

        if (crossesProjects && nodes.has(from) && nodes.has(to)) {
            const pair = between.get(`${from}->${to}`) ?? { source: from, target: to, endpoints: new Set<string>() };

            pair.endpoints.add(endpoint.id);
            between.set(`${from}->${to}`, pair);
        }
    }

    for (const { source, target, endpoints } of between.values()) {
        edges.push({
            source,
            target,
            type: "CALLS_ENDPOINTS_OF",
            properties: { label: endpoints.size === 1 ? "1 endpoint" : `${endpoints.size} endpoints` },
        });
    }

    // A call that requests an endpoint several ways is drawn once
    const drawn = new Set<string>();

    return {
        ...graph,
        edges: [
            ...graph.edges,
            ...edges.filter((edge) => {
                const key = `${edge.source}->${edge.type}->${edge.target}`;

                return drawn.has(key) ? false : Boolean(drawn.add(key));
            }),
        ],
    };
}

/**
 * The layers of several scans' graphs, each once, in the order they are
 * first found: an enhancer that ran on several scans is one layer.
 */
function mergeLayers(graphs: ScanGraph[]): ScanGraphLayer[] {
    const layers = new Map<string, ScanGraphLayer>();

    for (const graph of graphs) {
        for (const layer of graph.layers ?? []) {
            if (!layers.has(layer.id)) {
                layers.set(layer.id, layer);
            }
        }
    }

    return [...layers.values()];
}

/**
 * Exploring the graphs of a system's projects together: which projects
 * show, and at which of their scans, is chosen at the left, one scan a
 * project. Double-clicking a node focuses on it within its own scan, with
 * the way back to the system.
 */
export function SystemGraph({ systemId, projects, scans }: SystemGraphProps) {
    // The scan chosen per project, or null for a project left out; a
    // project not in it shows its latest completed scan
    const [chosen, setChosen] = useState<Record<string, string | null>>({});

    // The nodes focused on, in order, so the way back is known; the whole
    // system while empty
    const [trail, setTrail] = useState<Focus[]>([]);
    const focus = trail.at(-1) ?? null;

    const [selected, setSelected] = useState<string | null>(null);
    const [depth, setDepth] = useState<number | null>(null);
    const [layout, setLayout] = useState<ScanGraphLayout>("rings");

    const scansByProject = useMemo(() => {
        const byProject: Record<string, Scan[]> = {};

        for (const scan of completedScans(scans)) {
            (byProject[scan.projectId] ??= []).push(scan);
        }

        return byProject;
    }, [scans]);

    // The projects that show, each with the scan it shows at
    const shown = useMemo(
        () => projects
            .map((project) => ({
                project,
                scanId: project.id in chosen ? chosen[project.id] : scansByProject[project.id]?.[0]?.id ?? null,
            }))
            .filter((entry): entry is { project: Project; scanId: string } => entry.scanId !== null),
        [projects, chosen, scansByProject],
    );

    const scanIdOf = (project: Project): string | null =>
        shown.find((entry) => entry.project.id === project.id)?.scanId ?? null;

    const scanIds = shown.map(({ scanId }) => scanId);

    // The types of node left out of the picture; they are still loaded
    const [hiddenTypes, setHiddenTypes] = useState<string[]>([]);

    // The ids of the layers shown, of every scan; all of them while null
    const [layers, setLayers] = useState<string[] | null>(null);

    const system = useGetScanGraphs({ scanIds, depth, layers });
    const around = useGetScanGraph({ scanId: focus?.scanId, focus: focus?.nodeId, depth, layers });

    const merged = useMemo(
        () => mergeGraphs(
            system.graphs,
            Object.fromEntries(shown.map(({ project, scanId }) => [scanId, project.name])),
        ),
        [system.graphs, shown],
    );

    // How the scans shown are linked; found for those scans, not the latest
    const linked = useSystemLinks({ systemId, scanIds });

    const withLinksDrawn = useMemo(() => withLinks(merged, linked.links), [merged, linked.links]);

    const loaded = focus ? around.graph : withLinksDrawn;

    const graph = useMemo(() => withoutTypes(loaded, hiddenTypes), [loaded, hiddenTypes]);
    const loading = focus ? around.loading : system.loading;
    const error = focus ? around.error : system.error;

    const reset = () => {
        setTrail([]);
        setSelected(null);
    };

    const back = () => {
        setTrail((current) => current.slice(0, -1));
        setSelected(null);
    };

    const focusOn = (id: string) => {
        if (focus) {
            if (id !== (around.graph?.focus ?? null)) {
                setTrail((current) => [...current, { scanId: focus.scanId, nodeId: id }]);
                setSelected(id);
            }

            return;
        }

        // In the system's graph, the id says which scan the node is of
        const scanId = scanIds.find((candidate) => id.startsWith(`${candidate}:`));

        if (scanId) {
            const nodeId = id.slice(scanId.length + 1);

            setTrail([{ scanId, nodeId }]);
            setSelected(nodeId);
        }
    };

    /**
     * The scan a node shown is of, and its id in that scan's graph: as it
     * is while focused within one scan, and from its id in the system's
     * graph, which says which scan it is of.
     */
    const usagesOf = (id: string): { scanId: string; nodeId: string } | null => {
        if (focus) {
            return { scanId: focus.scanId, nodeId: id };
        }

        const scanId = scanIds.find((candidate) => id.startsWith(`${candidate}:`));

        return scanId ? { scanId, nodeId: id.slice(scanId.length + 1) } : null;
    };

    const choose = (project: Project, scanId: string | null) => {
        setChosen((current) => ({ ...current, [project.id]: scanId }));
        reset();
    };

    const selectedNode = graph?.nodes.find((node) => node.id === selected) ?? null;
    const focusNode = focus ? around.graph?.nodes.find((node) => node.id === around.graph?.focus) ?? null : null;
    const focusProject = focus ? shown.find(({ scanId }) => scanId === focus.scanId)?.project ?? null : null;
    const types = new Set(graph?.nodes.map((node) => node.type));

    return (
        <div className="space-y-4">
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
                        Whole system
                    </Button>
                    <Text as="span" size="sm" tone="secondary" truncate>
                        {focusNode ? (
                            <>
                                Around {NODE_TYPES[focusNode.type]?.label.toLowerCase() ?? focusNode.type}{" "}
                                <span className="font-medium text-foreground">{focusNode.name}</span>
                                {focusProject && <> in {focusProject.name}</>}
                            </>
                        ) : (
                            !focus && `${shown.length} of ${projects.length} projects`
                        )}
                    </Text>
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
                <div className="w-48">
                    <Field label="Layers">
                        {/* Of every scan shown, also while focused on a node of one */}
                        <LayerFilter
                            layers={merged?.layers ?? []}
                            shown={tickedLayers(merged?.layers ?? [], layers)}
                            onShownChange={(ticked) => setLayers(chosenLayers(merged?.layers ?? [], ticked))}
                        />
                    </Field>
                </div>
                <div className="w-44">
                    <Field label="Node types">
                        <NodeTypeFilter
                            types={nodeTypes(loaded)}
                            hidden={hiddenTypes}
                            onHiddenChange={setHiddenTypes}
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
            </div>

            <div className="flex flex-col gap-4 lg:flex-row">
                <aside className="w-full shrink-0 rounded-card border border-border bg-surface p-4 lg:w-72">
                    <Text size="sm" weight="medium">Projects</Text>
                    <ul className="mt-3 flex flex-col gap-4" aria-label="Projects">
                        {projects.map((project) => {
                            const projectScans = scansByProject[project.id] ?? [];
                            const scanId = scanIdOf(project);

                            return (
                                <li key={project.id} className="flex flex-col gap-2">
                                    <Checkbox
                                        label={project.name}
                                        checked={scanId !== null}
                                        disabled={projectScans.length === 0}
                                        onChange={() => choose(project, scanId === null ? projectScans[0]?.id ?? null : null)}
                                    />
                                    {projectScans.length === 0 ? (
                                        <Text size="xs" tone="muted">No completed scans yet</Text>
                                    ) : (
                                        <ScanSelector
                                            aria-label={`Scan of ${project.name}`}
                                            scans={projectScans}
                                            value={scanId}
                                            onScanChange={(scan) => choose(project, scan.id)}
                                            disabled={scanId === null}
                                        />
                                    )}
                                </li>
                            );
                        })}
                    </ul>
                </aside>

                <div className="flex min-w-0 flex-1 flex-col gap-4">
                    {error && (
                        <Alert tone="error" title="The graph could not be loaded" announce>
                            {error.message}
                        </Alert>
                    )}

                    {graph?.truncated && (
                        <Alert tone="warning">
                            There are more nodes within reach than fit: the nearest are shown. Focus on a node, or
                            choose a smaller depth, to see the rest.
                        </Alert>
                    )}

                    {shown.length === 0 ? (
                        <EmptyState
                            icon={GraphIcon}
                            title="No scans chosen"
                            description="The graph shows what scans found. Choose the projects to show, and which of their completed scans."
                        />
                    ) : (
                        <div className="relative h-[65vh] min-h-96 overflow-hidden rounded-card border border-border bg-surface">
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
                        </div>
                    )}

                    {types.size > 0 && shown.length > 0 && (
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
                </div>

                <aside className="w-full shrink-0 rounded-card border border-border bg-surface p-4 lg:w-80">
                    {selectedNode ? (
                        <NodeDetails
                            node={selectedNode}
                            isFocus={focus !== null && selectedNode.id === graph?.focus}
                            onFocus={focusOn}
                            usagesOf={usagesOf(selectedNode.id)}
                        />
                    ) : (
                        <Text size="sm" tone="muted">
                            Click a node to see what the scan found about it. Double-click it to focus the graph on
                            it.
                        </Text>
                    )}
                </aside>
            </div>

            {/* Only once there is something to show: links need HTTP calls
                with a url, which no scanner records yet, so until then the
                section, and a failure to load it, would only be noise */}
            {linked.links.length > 0 && (
                <section className="space-y-3 pt-4" aria-label="HTTP links">
                    <Text size="sm" weight="medium">How the projects call each other</Text>
                    <SystemLinks
                        links={linked.links}
                        calls={linked.calls}
                        endpoints={linked.endpoints}
                        projects={projects}
                        loading={linked.loading}
                        onFocus={(scanId, nodeId) => {
                            setTrail([{ scanId, nodeId }]);
                            setSelected(nodeId);
                        }}
                    />
                </section>
            )}
        </div>
    );
}
