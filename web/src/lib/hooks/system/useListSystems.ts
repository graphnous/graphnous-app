"use client";

import type { Page, System } from "@/types";
import { useFetch } from "@/lib/hooks/useFetch";

// Until they are loaded
const NO_SYSTEMS: System[] = [];

export function useListSystems() {
    const { data, loading, error, refresh } = useFetch<Page<System>>("/api/systems", "Failed to load systems");

    return {
        systems: data?.content ?? NO_SYSTEMS,
        loading,
        error,
        refresh,
    };
}
