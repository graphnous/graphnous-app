"use client";

import type { Page, SystemLibrary, SystemLibrarySort } from "@/types";
import { emptyPage } from "@/types/page";
import { useFetch } from "@/lib/hooks/useFetch";

export type UseSystemLibrariesOptions = {
    /**
     * The system whose libraries to list; nothing without one.
     */
    systemId: string | null | undefined;
    /**
     * Part of the library's name, ignoring case.
     */
    query?: string | null;
    /**
     * Only the libraries used in more than one version.
     */
    drift?: boolean;
    /**
     * From 0.
     */
    page?: number;
    /**
     * Up to 100; 50 when missing.
     */
    size?: number;
    sort?: SystemLibrarySort;
    direction?: "asc" | "desc";
};

const NO_LIBRARIES = emptyPage<SystemLibrary>();

/**
 * A page of the libraries the projects of a system depend on, each once
 * with every version in use, as the latest completed scan of each project
 * found them; loaded again when any option changes.
 */
export function useSystemLibraries({
    systemId,
    query,
    drift = false,
    page = 0,
    size = 50,
    sort = "name",
    direction = "asc",
}: UseSystemLibrariesOptions) {
    const search = new URLSearchParams({ page: String(page), size: String(size), sort, direction });

    if (query?.trim()) {
        search.set("query", query.trim());
    }

    if (drift) {
        search.set("drift", "true");
    }

    const { data, loading, error, refresh } = useFetch<Page<SystemLibrary>>(
        systemId ? `/api/systems/${encodeURIComponent(systemId)}/libraries?${search}` : null,
        "Failed to load the system's libraries",
    );

    return {
        libraries: data?.content ?? NO_LIBRARIES.content,
        page: data ?? NO_LIBRARIES,
        loading,
        error,
        refresh,
    };
}
