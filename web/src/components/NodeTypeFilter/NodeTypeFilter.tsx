"use client";

import { Button, Checkbox, Popover, Text } from "@graphnous/theme";
import { CaretDownIcon, FunnelIcon } from "@phosphor-icons/react";

import { NODE_TYPES } from "@/components/ScanGraphCanvas/graphStyle";
import type { ScanGraph, ScanGraphNodeType } from "@/types";

/**
 * What a type of node is called: as the legend calls it, or by its label
 * in the graph for a type an enhancer added.
 */
export function typeLabel(type: string): string {
    return NODE_TYPES[type as ScanGraphNodeType]?.label ?? type;
}

/**
 * The types of the graph's nodes, each once: those the legend knows first,
 * in its order, then the others by name.
 */
export function nodeTypes(graph: ScanGraph | null | undefined): string[] {
    const present = new Set<string>(graph?.nodes.map((node) => node.type));
    const known = Object.keys(NODE_TYPES).filter((type) => present.has(type));
    const others = [...present].filter((type) => !(type in NODE_TYPES)).sort();

    return [...known, ...others];
}

/**
 * The graph without the nodes of the hidden types, and without the
 * relationships to them. Its focus stays, whatever type it is of.
 */
export function withoutTypes<Graph extends ScanGraph | null | undefined>(graph: Graph, hidden: string[]): Graph {
    if (!graph || hidden.length === 0) {
        return graph;
    }

    const nodes = graph.nodes.filter((node) => node.id === graph.focus || !hidden.includes(node.type));
    const shown = new Set(nodes.map((node) => node.id));

    return {
        ...graph,
        nodes,
        edges: graph.edges.filter((edge) => shown.has(edge.source) && shown.has(edge.target)),
    };
}

export type NodeTypeFilterProps = {
    /**
     * The types of the nodes of the graph, before any is hidden.
     */
    types: string[];
    /**
     * The types whose nodes are hidden; none of them at first. Kept as
     * what is hidden, so a type that only shows up later, such as after
     * focusing on another node, shows.
     */
    hidden: string[];
    onHiddenChange: (hidden: string[]) => void;
    disabled?: boolean;
};

/**
 * Choosing which types of node a graph shows, such as only classes and
 * methods: a button saying how many show, which opens a list to tick them
 * on and off.
 */
export function NodeTypeFilter({ types, hidden, onHiddenChange, disabled }: NodeTypeFilterProps) {
    const toggle = (type: string) =>
        onHiddenChange(hidden.includes(type) ? hidden.filter((other) => other !== type) : [...hidden, type]);

    return (
        <Popover
            label="Node types"
            placement="bottom-end"
            trigger={
                <Button
                    variant="secondary"
                    icon={FunnelIcon}
                    iconEnd={CaretDownIcon}
                    disabled={disabled || types.length === 0}
                    fullWidth
                >
                    <span className="truncate">{summary(types, hidden)}</span>
                </Button>
            }
        >
            <div className="flex min-w-56 flex-col gap-3">
                <Text size="sm" weight="medium">Show which nodes</Text>
                {types.map((type) => (
                    <div key={type} className="flex items-center gap-2">
                        <Checkbox
                            label={typeLabel(type)}
                            checked={!hidden.includes(type)}
                            onChange={() => toggle(type)}
                        />
                        {type in NODE_TYPES && (
                            <span
                                className="size-2.5 shrink-0 rounded-full"
                                style={{ backgroundColor: `var(${NODE_TYPES[type as ScanGraphNodeType].token})` }}
                                aria-hidden
                            />
                        )}
                    </div>
                ))}
                <div className="flex gap-2">
                    <Button variant="ghost" size="sm" disabled={hidden.length === 0} onClick={() => onHiddenChange([])}>
                        Show all
                    </Button>
                    <Button
                        variant="ghost"
                        size="sm"
                        disabled={types.every((type) => hidden.includes(type))}
                        // Besides the types hidden that the graph does not have now
                        onClick={() => onHiddenChange([...new Set([...hidden, ...types])])}
                    >
                        Hide all
                    </Button>
                </div>
            </div>
        </Popover>
    );
}

/**
 * What the button says: all, none, the one shown, or how many of them.
 */
export function summary(types: string[], hidden: string[]): string {
    const shown = types.filter((type) => !hidden.includes(type));

    if (shown.length === types.length) {
        return "All types";
    }

    if (shown.length === 0) {
        return "No types";
    }

    return shown.length === 1 ? typeLabel(shown[0]) : `${shown.length} of ${types.length} types`;
}
