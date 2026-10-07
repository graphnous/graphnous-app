"use client";

import { Button, Code, CopyButton, DescriptionList, Heading, Text } from "@graphnous/theme";
import { CrosshairIcon } from "@phosphor-icons/react";

import type { ScanGraphNode } from "@/types";

import { NODE_TYPES } from "./graphStyle";

export type NodeDetailsProps = {
    node: ScanGraphNode;
    /**
     * Whether the graph is already around this node.
     */
    isFocus: boolean;
    onFocus: (id: string) => void;
};

/**
 * A property as it reads: lists joined, anything else as JSON.
 */
function formatValue(value: unknown): string {
    if (Array.isArray(value)) {
        return value.map((item) => (typeof item === "string" ? item : JSON.stringify(item))).join(", ");
    }

    return typeof value === "string" ? value : JSON.stringify(value);
}

/**
 * What the scan found about a node of its graph, and focusing on it.
 */
export function NodeDetails({ node, isFocus, onFocus }: NodeDetailsProps) {
    const style = NODE_TYPES[node.type];

    const properties = Object.entries(node.properties)
        .filter(([, value]) => value !== null && value !== "" && !(Array.isArray(value) && value.length === 0))
        .map(([name, value]) => ({
            label: name,
            value: <span className="break-all font-mono text-xs">{formatValue(value)}</span>,
        }));

    return (
        <div className="space-y-4">
            <div className="space-y-1">
                <div className="flex items-center gap-2">
                    <span
                        className="size-3 shrink-0 rounded-full"
                        style={{ backgroundColor: `var(${style?.token ?? "--color-foreground-muted"})` }}
                        aria-hidden
                    />
                    <Text as="span" size="xs" tone="muted">{style?.label ?? node.type}</Text>
                    {node.depth > 0 && (
                        <Text as="span" size="xs" tone="muted">
                            · {node.depth === 1 ? "1 hop" : `${node.depth} hops`} from the focus
                        </Text>
                    )}
                </div>
                <Heading level={3} size="sm" className="break-all">{node.name ?? node.type}</Heading>
                <div className="flex items-center gap-1">
                    <Code className="min-w-0 truncate text-xs" >{node.id}</Code>
                    <CopyButton value={node.id} label="Copy the node's id" size="sm" />
                </div>
            </div>

            <Button
                variant="secondary"
                size="sm"
                icon={CrosshairIcon}
                disabled={isFocus}
                onClick={() => onFocus(node.id)}
            >
                {isFocus ? "In focus" : "Focus on this node"}
            </Button>

            {properties.length > 0 && <DescriptionList items={properties} />}
        </div>
    );
}
