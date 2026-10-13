import type { StylesheetJson } from "cytoscape";

import type { ScanGraphNodeType } from "@/types";
import { resolveColors as resolveTokens } from "@/lib/theme/resolveColors";

/**
 * How a type of node looks: its colour, as a theme token, its shape and
 * size, and what to call it in a legend. Related types share a colour and
 * differ by shape.
 */
export type NodeTypeStyle = {
    label: string;
    token: string;
    shape:
        | "ellipse"
        | "round-rectangle"
        | "rectangle"
        | "cut-rectangle"
        | "round-diamond"
        | "diamond"
        | "rhomboid"
        | "hexagon"
        | "round-hexagon"
        | "round-pentagon"
        | "round-octagon"
        | "round-triangle"
        | "barrel"
        | "tag"
        | "round-tag"
        | "vee"
        | "star";
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

    // An enhancer that ran on the scan, which what it added hangs from
    Enhancement: { label: "Enhancer", token: "--color-foreground-secondary", shape: "star", size: 30 },

    // The building blocks of a user interface, in one colour
    Component: { label: "Component", token: "--chart-series-1", shape: "round-hexagon", size: 24 },
    Directive: { label: "Directive", token: "--chart-series-1", shape: "round-triangle", size: 20 },
    NgModule: { label: "Angular module", token: "--chart-series-1", shape: "cut-rectangle", size: 26 },

    // What an application is wired from, in another
    Application: { label: "Application", token: "--chart-series-8", shape: "star", size: 28 },
    Configuration: { label: "Configuration", token: "--chart-series-8", shape: "cut-rectangle", size: 22 },
    Controller: { label: "Controller", token: "--chart-series-8", shape: "round-pentagon", size: 24 },
    Service: { label: "Service", token: "--chart-series-8", shape: "round-octagon", size: 22 },
    Repository: { label: "Repository", token: "--chart-series-8", shape: "barrel", size: 22 },
    Bean: { label: "Bean", token: "--chart-series-8", shape: "ellipse", size: 16 },

    // What is served and called over HTTP, in the colour of endpoints
    Route: { label: "Route", token: "--color-graph-endpoint", shape: "rhomboid", size: 24 },
    Endpoint: { label: "Endpoint", token: "--color-graph-endpoint", shape: "diamond", size: 22 },
    PathVariable: { label: "Path variable", token: "--color-graph-endpoint", shape: "ellipse", size: 12 },
    HttpCall: { label: "HTTP call", token: "--color-graph-endpoint", shape: "vee", size: 18 },
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
 * Resolves the graph's colours as they apply inside the element, in the
 * current colour scheme.
 */
export function resolveColors(element: HTMLElement): GraphColors {
    const tokens = Object.entries(BASE_TOKENS);
    const nodeTokens = Object.values(NODE_TYPES).map((style) => style.token);
    const resolved = resolveTokens(element, [...tokens.map(([, token]) => token), ...nodeTokens]);

    const colors: Record<string, string> = {};

    for (const [name, token] of tokens) {
        colors[name] = resolved[token];
    }

    for (const token of nodeTokens) {
        colors[token] = resolved[token];
    }

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
                // A type an enhancer added that has no look of its own yet
                "background-color": colors.muted,
                shape: "ellipse",
                width: 16,
                height: 16,
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
            // What ties an enhancer and what it added to the scan: drawn
            // quietly, as it says where a node comes from, not what it does
            selector: 'edge[type = "HAS_ENHANCEMENT"], edge[type = "ADDED"], edge[type = "ENHANCE"]',
            style: {
                "line-style": "dashed",
                "target-arrow-shape": "none",
                opacity: 0.35,
            },
        },
        {
            selector: 'edge[type = "RENDERS"], edge[type = "IMPORTS"], edge[type = "INJECTS"]',
            style: {
                width: 1.5,
                "line-color": colors["--chart-series-1"],
                "target-arrow-color": colors["--chart-series-1"],
                opacity: 0.9,
            },
        },
        {
            selector: 'edge[type = "INJECTS"]',
            style: { "line-style": "dashed" },
        },
        {
            selector: 'edge[type = "HAS_ENDPOINT"], edge[type = "HAS_PATH_VARIABLE"], edge[type = "HAS_HTTP_CALL"]',
            style: {
                "line-color": colors["--color-graph-endpoint"],
                "target-arrow-color": colors["--color-graph-endpoint"],
            },
        },
        {
            // Between projects: what makes a system of them
            selector: 'edge[type = "CALLS_ENDPOINTS_OF"]',
            style: {
                width: 3,
                "line-color": colors["--color-graph-endpoint"],
                "target-arrow-color": colors["--color-graph-endpoint"],
                "arrow-scale": 1,
                label: "data(label)",
                "font-size": 10,
                color: colors.foreground,
                "text-background-color": colors.background,
                "text-background-opacity": 0.85,
                "text-background-padding": "2px",
                "text-rotation": "autorotate",
                opacity: 1,
            },
        },
        {
            selector: 'edge[type = "REQUESTS"]',
            style: {
                width: 1.5,
                "line-color": colors["--color-graph-endpoint"],
                "target-arrow-color": colors["--color-graph-endpoint"],
                "line-style": "dashed",
                opacity: 0.9,
            },
        },
        {
            // What bodies use, of the usages layer: calls stand out most
            selector: 'edge[type = "CALLS"], edge[type = "INSTANTIATES"], edge[type = "READS"], edge[type = "WRITES"], edge[type = "USES"]',
            style: {
                width: 1.5,
                "line-color": colors["--color-graph-method"],
                "target-arrow-color": colors["--color-graph-method"],
                label: "data(label)",
                "font-size": 8,
                color: colors.muted,
                "text-background-color": colors.background,
                "text-background-opacity": 0.85,
                "text-background-padding": "1px",
                "text-rotation": "autorotate",
                "min-zoomed-font-size": 7,
                opacity: 0.9,
            },
        },
        {
            selector: 'edge[type = "INSTANTIATES"]',
            style: {
                "line-color": colors["--color-graph-class"],
                "target-arrow-color": colors["--color-graph-class"],
                "line-style": "dashed",
            },
        },
        {
            selector: 'edge[type = "READS"], edge[type = "WRITES"]',
            style: {
                width: 1,
                "line-color": colors.muted,
                "target-arrow-color": colors.muted,
            },
        },
        {
            selector: 'edge[type = "WRITES"]',
            style: { "line-style": "dashed" },
        },
        {
            selector: 'edge[type = "USES"]',
            style: {
                width: 1,
                "line-color": colors.border,
                "target-arrow-color": colors.border,
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
