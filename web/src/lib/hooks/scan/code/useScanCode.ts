"use client";

import type { Page, ScanCodeItems, ScanCodeKind } from "@/types";
import { emptyPage } from "@/types/page";
import { useFetch } from "@/lib/hooks/useFetch";

export type UseScanCodeOptions<Kind extends ScanCodeKind> = {
    /**
     * The scan whose code to list; nothing without one.
     */
    scanId: string | null | undefined;
    /**
     * What to list: its modules, packages, files, classes or methods.
     */
    kind: Kind;
    /**
     * Part of the name, qualified name or path, ignoring case.
     */
    query?: string | null;
    /**
     * From 0.
     */
    page?: number;
    /**
     * Up to 100; 50 when missing.
     */
    size?: number;
    /**
     * One of the properties the kind sorts by; the API's default when
     * missing.
     */
    sort?: string | null;
    direction?: "asc" | "desc";
};

const NO_ITEMS = emptyPage<never>();

/**
 * A page of what a scan found of one kind, loaded again when any option
 * changes.
 */
export function useScanCode<Kind extends ScanCodeKind>({
    scanId,
    kind,
    query,
    page = 0,
    size = 50,
    sort,
    direction = "asc",
}: UseScanCodeOptions<Kind>) {
    const search = new URLSearchParams({ page: String(page), size: String(size), direction });

    if (sort) {
        search.set("sort", sort);
    }

    if (query?.trim()) {
        search.set("query", query.trim());
    }

    const { data, loading, error, refresh } = useFetch<Page<ScanCodeItems[Kind]>>(
        scanId ? `/api/scans/${encodeURIComponent(scanId)}/${kind}?${search}` : null,
        `Failed to load the ${kind}`,
    );

    return {
        items: data?.content ?? (NO_ITEMS.content as ScanCodeItems[Kind][]),
        page: data ?? (NO_ITEMS as Page<ScanCodeItems[Kind]>),
        loading,
        error,
        refresh,
    };
}
