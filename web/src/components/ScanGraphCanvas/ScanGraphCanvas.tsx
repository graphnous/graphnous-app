"use client";

import cytoscape, { type Core, type ElementDefinition, type LayoutOptions, type Layouts } from "cytoscape";
import { useEffect, useRef } from "react";

import type { ScanGraph, ScanGraphNode } from "@/types";

import { graphStylesheet, resolveColors } from "./graphStyle";

export type ScanGraphLayout = "rings" | "tree";

export type ScanGraphCanvasProps = {
    graph: ScanGraph;
    /**
     * rings: the focus in the middle and each hop a ring around it; tree:
     * the focus on top and each hop a row below it.
     */
    layout?: ScanGraphLayout;
    /**
     * The id of the selected node, if any.
     */
    selected?: string | null;
    /**
     * Called with the id of the node clicked, or null when the background
     * is.
     */
    onSelect?: (id: string | null) => void;
    /**
     * Called with the id of the node double-clicked, such as to center the
     * graph on it.
     */
    onFocus?: (id: string) => void;
    className?: string;
};

/**
 * What a node is called on the canvas, short enough to read there: a file
 * by its name, as its path is in its details.
 */
function label(node: ScanGraphNode): string {
    const name = node.name ?? node.type;

    switch (node.type) {
        case "Method":
            return `${name}()`;
        case "File":
            return name.split("/").at(-1) ?? name;
        default:
            return name;
    }
}

/**
 * The cytoscape elements of the graph. Edges are known by their ends and
 * type, which is all that makes them different.
 */
function elements(graph: ScanGraph): ElementDefinition[] {
    const nodes: ElementDefinition[] = graph.nodes.map((node) => ({
        group: "nodes",
        data: {
            id: node.id,
            type: node.type,
            depth: node.depth,
            label: label(node),
        },
        classes: node.id === graph.focus ? "focus" : undefined,
    }));

    const edges: ElementDefinition[] = graph.edges.map((edge) => ({
        group: "edges",
        data: {
            id: `${edge.source}->${edge.type}->${edge.target}`,
            source: edge.source,
            target: edge.target,
            type: edge.type,
        },
    }));

    return [...nodes, ...edges];
}

function layoutOptions(cy: Core, graph: ScanGraph, layout: ScanGraphLayout): LayoutOptions {
    const shared = {
        // Hidden pages get no animation frames, which would leave the nodes
        // halfway; they are placed at once instead
        animate: document.visibilityState === "visible",
        animationDuration: 300,
        fit: true,
        padding: 32,
    };

    if (layout === "tree") {
        return {
            ...shared,
            name: "breadthfirst",
            directed: false,
            spacingFactor: 1.1,
            // A collection rather than a selector, as ids are not valid in one
            roots: cy.getElementById(graph.focus) as unknown as string[],
        };
    }

    const deepest = Math.max(0, ...graph.nodes.map((node) => node.depth));

    return {
        ...shared,
        name: "concentric",
        minNodeSpacing: 24,
        // The nearer to the focus, the nearer to the middle
        concentric: (node) => deepest - node.data("depth"),
        levelWidth: () => 1,
    };
}

/**
 * Draws a part of a scan's graph, as useGetScanGraph returns it: click a
 * node to select it, double-click it to focus on it. When the graph
 * changes, the nodes it keeps move to their new places rather than being
 * drawn anew, and new ones grow out of the focus.
 */
export function ScanGraphCanvas({
    graph,
    layout = "rings",
    selected = null,
    onSelect,
    onFocus,
    className,
}: ScanGraphCanvasProps) {
    const container = useRef<HTMLDivElement>(null);
    const cy = useRef<Core | null>(null);

    // Lays out the current graph again
    const relayout = useRef<(() => void) | null>(null);
    const running = useRef<Layouts | null>(null);

    // The latest handlers, so changing them does not draw the graph anew
    const handlers = useRef({ onSelect, onFocus });

    useEffect(() => {
        handlers.current = { onSelect, onFocus };
    });

    // The graph itself, once
    useEffect(() => {
        if (!container.current) {
            return;
        }

        const instance = cytoscape({
            container: container.current,
            style: graphStylesheet(resolveColors(container.current)),
            minZoom: 0.1,
            maxZoom: 3,
            wheelSensitivity: 0.3,
            boxSelectionEnabled: false,
            selectionType: "single",
        });

        instance.on("tap", "node", (event) => handlers.current.onSelect?.(event.target.id()));
        instance.on("tap", (event) => {
            if (event.target === instance) {
                handlers.current.onSelect?.(null);
            }
        });
        instance.on("dbltap", "node", (event) => handlers.current.onFocus?.(event.target.id()));

        // Drawn again in the colours of the other scheme when it changes
        const restyle = () => {
            if (container.current) {
                instance.style(graphStylesheet(resolveColors(container.current)));
            }
        };

        const scheme = window.matchMedia("(prefers-color-scheme: dark)");
        scheme.addEventListener("change", restyle);

        const theme = new MutationObserver(restyle);
        theme.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme", "class"] });

        // A graph laid out while the container had no size yet, such as
        // right after it mounts, is laid out again once it has one
        let sized = false;

        const resize = new ResizeObserver(([entry]) => {
            const hasSize = entry.contentRect.width > 0 && entry.contentRect.height > 0;

            instance.resize();

            if (hasSize && !sized) {
                relayout.current?.();
            }

            sized = hasSize;
        });
        resize.observe(container.current);

        cy.current = instance;

        return () => {
            scheme.removeEventListener("change", restyle);
            theme.disconnect();
            resize.disconnect();
            instance.destroy();
            cy.current = null;
        };
    }, []);

    // The nodes and edges, kept where they stay and laid out again
    useEffect(() => {
        const instance = cy.current;

        if (!instance) {
            return;
        }

        const next = elements(graph);
        const ids = new Set(next.map((element) => element.data.id));

        instance.batch(() => {
            instance.elements().filter((element) => !ids.has(element.id())).remove();

            const focus = instance.getElementById(graph.focus);
            const origin = focus.nonempty() ? { ...focus.position() } : undefined;

            for (const element of next) {
                const existing = instance.getElementById(element.data.id!);

                if (existing.nonempty()) {
                    existing.data(element.data);
                    existing.classes(element.classes ?? "");
                } else {
                    instance.add(element.group === "nodes" && origin ? { ...element, position: { ...origin } } : element);
                }
            }
        });

        relayout.current = () => {
            // Two layouts animating at once leave the nodes halfway between both
            running.current?.stop();
            running.current = instance.layout(layoutOptions(instance, graph, layout));
            running.current.run();
        };
        relayout.current();
    }, [graph, layout]);

    // The selection, as the parent keeps it
    useEffect(() => {
        const instance = cy.current;

        if (!instance) {
            return;
        }

        instance.nodes(":selected").unselect();

        if (selected) {
            instance.getElementById(selected).select();
        }
    }, [selected, graph]);

    return (
        <div
            ref={container}
            role="img"
            aria-label={`Graph of ${graph.nodes.length} nodes and ${graph.edges.length} relationships`}
            className={className}
        />
    );
}
