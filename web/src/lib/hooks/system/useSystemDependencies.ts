"use client";

import type { Page, ScanDependencySort, SystemDependency } from "@/types";
import { emptyPage } from "@/types/page";
import { useFetch } from "@/lib/hooks/useFetch";

export type UseSystemDependenciesOptions = {
    /**
     * The system whose projects' dependencies to list; nothing without one.
     */
    systemId: string | null | undefined;
    /**
     * Part of the library's name, ignoring case.
     */
    query?: string | null;
    /**
     * The path of the module that depends on them.
     */
    module?: string | null;
    /**
     * Such as compile or test, ignoring case.
     */
    scope?: string | null;
    /**
     * From 0.
     */
    page?: number;
    /**
     * Up to 100; 50 when missing.
     */
    size?: number;
    sort?: ScanDependencySort;
    direction?: "asc" | "desc";
};

const NO_DEPENDENCIES = emptyPage<SystemDependency>();

/**
 * A page of the libraries the projects of a system depend on, those of the
 * latest completed scan of each, one per project, module and library,
 * loaded again when any option changes.
 */
export function useSystemDependencies({
    systemId,
    query,
    module,
    scope,
    page = 0,
    size = 50,
    sort = "name",
    direction = "asc",
}: UseSystemDependenciesOptions) {
    const search = new URLSearchParams({ page: String(page), size: String(size), sort, direction });

    for (const [name, value] of Object.entries({ query, module, scope })) {
        if (value?.trim()) {
            search.set(name, value.trim());
        }
    }

    const { data, loading, error, refresh } = useFetch<Page<SystemDependency>>(
        systemId ? `/api/systems/${encodeURIComponent(systemId)}/dependencies?${search}` : null,
        "Failed to load the system's dependencies",
    );

    return {
        dependencies: data?.content ?? NO_DEPENDENCIES.content,
        page: data ?? NO_DEPENDENCIES,
        loading,
        error,
        refresh,
    };
}
