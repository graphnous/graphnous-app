"use client";

import { useCallback, useEffect, useState } from "react";

import type { ScanGraph } from "@/types";

export type UseGetScanGraphOptions = {
    scanId: string | null | undefined;
    /**
     * The id of the node to center the graph on; the scan itself when
     * missing.
     */
    focus?: string | null;
    /**
     * The hops from the focus, 0 to 5; the API's default when missing: 2
     * around the scan, 1 around a node.
     */
    depth?: number | null;
};

/**
 * What the last answered request returned, and which request that was.
 */
type Loaded = {
    request: string | null;
    graph: ScanGraph | null;
    error: Error | null;
};

/**
 * The part of a scan's graph around a node, loaded again when the scan,
 * focus or depth change. While the next one loads, the last one stays, so
 * a graph being explored does not blink out; a request that answers after
 * a newer one was made is ignored.
 */
export function useGetScanGraph({ scanId, focus, depth }: UseGetScanGraphOptions) {
    const [loaded, setLoaded] = useState<Loaded>({ request: null, graph: null, error: null });

    // Counts refreshes, so refreshing makes a request of its own
    const [refreshes, setRefreshes] = useState(0);

    const path = scanId ? graphPath(scanId, focus, depth) : null;
    const request = path ? `${path}#${refreshes}` : null;

    useEffect(() => {
        if (!path || !request) {
            return;
        }

        let current = true;

        fetchGraph(path)
            .then(
                (graph) => current && setLoaded({ request, graph, error: null }),
                (error: unknown) => current && setLoaded((last) => ({
                    request,
                    graph: last.graph,
                    error: error instanceof Error ? error : new Error("Failed to load the scan graph"),
                })),
            );

        return () => {
            current = false;
        };
    }, [path, request]);

    const refresh = useCallback(() => setRefreshes((count) => count + 1), []);

    return {
        graph: scanId ? loaded.graph : null,
        loading: request !== null && loaded.request !== request,
        error: request !== null && loaded.request === request ? loaded.error : null,
        refresh,
    };
}

function graphPath(scanId: string, focus?: string | null, depth?: number | null): string {
    const query = new URLSearchParams();

    if (focus) {
        query.set("focus", focus);
    }

    if (depth !== null && depth !== undefined) {
        query.set("depth", String(depth));
    }

    const search = query.size > 0 ? `?${query}` : "";

    return `/api/scans/${encodeURIComponent(scanId)}/graph${search}`;
}

async function fetchGraph(path: string): Promise<ScanGraph> {
    const response = await fetch(path);

    if (!response.ok) {
        throw new Error(await errorMessage(response));
    }

    return response.json();
}

/**
 * Why the API refused, such as a focus that is not in the scan's graph.
 */
async function errorMessage(response: Response): Promise<string> {
    try {
        const body: { message?: string } = await response.json();

        if (body.message) {
            return body.message;
        }
    } catch {
        // Not JSON; say what failed instead
    }

    return "Failed to load the scan graph";
}
