"use client";

import type { Enhancer } from "@/types";
import { useFetch } from "@/lib/hooks/useFetch";

const NO_ENHANCERS: Enhancer[] = [];

/**
 * Every enhancer installed on the server, in the order they run; none
 * while not enabled, such as while the dialog that lists them is closed.
 */
export function useListEnhancers(enabled = true) {
    const { data, loading, error, refresh } = useFetch<Enhancer[]>(
        enabled ? "/api/enhancers" : null,
        "Failed to load the enhancers",
    );

    return {
        enhancers: data ?? NO_ENHANCERS,
        loading,
        error,
        refresh,
    };
}
