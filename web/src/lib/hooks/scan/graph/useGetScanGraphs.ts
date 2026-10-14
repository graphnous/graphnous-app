"use client";

import { useEffect, useMemo, useState } from "react";

import type { ScanGraph } from "@/types";

import { fetchGraph, graphPath } from "./useGetScanGraph";

export type UseGetScanGraphsOptions = {
    /**
     * The scans whose graphs to load, each around the scan itself.
     */
    scanIds: string[];
    /**
     * The hops from each scan, 0 to 5; the API's default when missing.
     */
    depth?: number | null;
    /**
     * The ids of the layers to show of each graph; every layer when
     * missing.
     */
    layers?: string[] | null;
};

/**
 * What a request returned: its graph, or why it failed.
 */
type Answer = { graph: ScanGraph } | { error: Error };

/**
 * The graphs of several scans, each around its scan, in the order of the
 * scans. A graph once loaded is kept, so adding or leaving out a scan only
 * loads what is new; the graphs of scans still loading or failed are left
 * out.
 */
export function useGetScanGraphs({ scanIds, depth, layers }: UseGetScanGraphsOptions) {
    // By path, so the same scan at another depth, or of other layers, is a graph of its own
    const [answers, setAnswers] = useState<Record<string, Answer>>({});

    const paths = scanIds.map((scanId) => graphPath(scanId, null, depth, layers));
    const missing = paths.filter((path) => !(path in answers));

    // The paths as one value, so the effect runs when they change
    const wanted = missing.join("\n");

    useEffect(() => {
        if (!wanted) {
            return;
        }

        let current = true;

        for (const path of wanted.split("\n")) {
            fetchGraph(path).then(
                (graph) => current && setAnswers((last) => ({ ...last, [path]: { graph } })),
                (error: unknown) => current && setAnswers((last) => ({
                    ...last,
                    [path]: { error: error instanceof Error ? error : new Error("Failed to load the scan graph") },
                })),
            );
        }

        return () => {
            current = false;
        };
    }, [wanted]);

    const asked = paths.join("\n");

    // The same list until a graph or the scans change, so it can be depended on
    const { graphs, error } = useMemo(() => {
        const graphs: ScanGraph[] = [];
        let error: Error | null = null;

        for (const path of asked ? asked.split("\n") : []) {
            const answer = answers[path];

            if (answer && "graph" in answer) {
                graphs.push(answer.graph);
            } else if (answer) {
                error ??= answer.error;
            }
        }

        return { graphs, error };
    }, [answers, asked]);

    return {
        graphs,
        loading: missing.length > 0,
        error,
    };
}
