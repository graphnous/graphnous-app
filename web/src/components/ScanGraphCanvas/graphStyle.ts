import type { StylesheetJson } from "cytoscape";

import type { ScanGraphNodeType } from "@/types";

/**
 * How a type of node looks: its colour, as a theme token, its shape and
 * size, and what to call it in a legend.
 */
export type NodeTypeStyle = {
    label: string;
    token: string;
    shape: "ellipse" | "round-rectangle" | "rectangle" | "round-diamond" | "hexagon" | "barrel" | "tag" | "round-tag";
    size: number;
};

export const NODE_TYPES: Record<ScanGraphNodeType, NodeTypeStyle> = {
    Scan: { label: "Scan", token: "--color-graph-selected", shape: "round-diamond", size: 40 },
    ScanTarget: { label: "Target", token: "--color-graph-project", shape: "hexagon", size: 34 },
    Module: { label: "Module", token: "--color-graph-project", shape: "round-rectangle", size: 30 },
    File: { label: "File", token: "--color-foreground-muted", shape: "rectangle", size: 20 },
    Package: { label: "Package", token: "--color-graph-package", shape: "round-rectangle", size: 24 },
    Class: { label: "Class", token: "--color-graph-class", shape: "ellipse", size: 24 },
    Method: { label: "Method", token: "--color-graph-method", shape: "ellipse", size: 14 },
    Field: { label: "Field", token: "--color-foreground-secondary", shape: "ellipse", size: 12 },
    Annotation: { label: "Annotation", token: "--color-graph-endpoint", shape: "round-tag", size: 14 },
    Dependency: { label: "Dependency", token: "--color-graph-external-dependency", shape: "barrel", size: 20 },
};

/**
 * The colours the graph is drawn in, resolved from the theme: the canvas
 * cannot use CSS variables.
 */
export type GraphColors = Record<string, string> & {
    background: string;
    foreground: string;
    muted: string;
    border: string;
    selected: string;
};

const BASE_TOKENS = {
    background: "--color-surface",
    foreground: "--color-foreground",
    muted: "--color-foreground-muted",
    border: "--color-border-strong",
    selected: "--color-graph-selected",
};

/**
 * Resolves the theme's colours as they apply inside the element, in the
 * current colour scheme, to rgb() the canvas can draw with: the theme's
 * colours use light-dark() and color-mix(), which it cannot.
 */
export function resolveColors(element: HTMLElement): GraphColors {
    const probe = document.createElement("span");
    probe.style.display = "none";
    element.appendChild(probe);

    const canvas = document.createElement("canvas");
    canvas.width = canvas.height = 1;
    const context = canvas.getContext("2d", { willReadFrequently: true });

    const resolve = (token: string) => {
        probe.style.color = `var(${token})`;
        const color = getComputedStyle(probe).color;

        if (!context) {
            return color;
        }

        // Drawn and read back, which turns any CSS colour into rgb
        context.clearRect(0, 0, 1, 1);
        context.fillStyle = color;
        context.fillRect(0, 0, 1, 1);
        const [r, g, b] = context.getImageData(0, 0, 1, 1).data;

        return `rgb(${r}, ${g}, ${b})`;
    };

    const colors: Record<string, string> = {};

    for (const [name, token] of Object.entries(BASE_TOKENS)) {
        colors[name] = resolve(token);
    }

    for (const style of Object.values(NODE_TYPES)) {
        colors[style.token] ??= resolve(style.token);
    }

    probe.remove();

    return colors as GraphColors;
}

/**
 * The stylesheet of the graph: nodes by type, the focus ringed, the
 * selected node highlighted; containment drawn quietly, inheritance and
 * dependencies stand out.
 */
export function graphStylesheet(colors: GraphColors): StylesheetJson {
    return [
        {
            selector: "node",
            style: {
                label: "data(label)",
                color: colors.foreground,
                "font-size": 10,
                "text-valign": "bottom",
                "text-margin-y": 4,
                "text-background-color": colors.background,
                "text-background-opacity": 0.75,
                "text-background-padding": "2px",
                "text-max-width": "140px",
                "text-wrap": "ellipsis",
                // Labels go when they would be unreadably small
                "min-zoomed-font-size": 7,
                "border-width": 0,
                "overlay-padding": 4,
            },
        },
        ...Object.entries(NODE_TYPES).map(([type, style]) => ({
            selector: `node[type = "${type}"]`,
            style: {
                "background-color": colors[style.token],
                shape: style.shape,
                width: style.size,
                height: style.size,
            },
        })),
        {
            selector: "node.focus",
            style: {
                "border-width": 4,
                "border-color": colors.selected,
                "font-weight": "bold",
                "font-size": 12,
            },
        },
        {
            selector: "node:selected",
            style: {
                "border-width": 3,
                "border-color": colors.foreground,
                "overlay-color": colors.selected,
                "overlay-opacity": 0.2,
            },
        },
        {
            selector: "edge",
            style: {
                width: 1,
                "line-color": colors.border,
                "target-arrow-color": colors.border,
                "target-arrow-shape": "triangle",
                "arrow-scale": 0.6,
                "curve-style": "bezier",
                opacity: 0.7,
            },
        },
        {
            selector: 'edge[type = "EXTENDS"], edge[type = "IMPLEMENTS"]',
            style: {
                width: 2,
                "line-color": colors["--color-graph-class"],
                "target-arrow-color": colors["--color-graph-class"],
                "target-arrow-shape": "triangle",
                "target-arrow-fill": "hollow",
                label: "data(type)",
                "font-size": 8,
                color: colors.muted,
                "text-rotation": "autorotate",
                "min-zoomed-font-size": 7,
                opacity: 1,
            },
        },
        {
            selector: 'edge[type = "IMPLEMENTS"]',
            style: { "line-style": "dashed" },
        },
        {
            selector: 'edge[type = "DEPENDS_ON"]',
            style: {
                "line-color": colors["--color-graph-external-dependency"],
                "target-arrow-color": colors["--color-graph-external-dependency"],
                "line-style": "dotted",
            },
        },
        {
            selector: 'edge[type = "ANNOTATED_WITH"]',
            style: {
                "line-color": colors["--color-graph-endpoint"],
                "target-arrow-shape": "none",
                opacity: 0.5,
            },
        },
    ];
}
