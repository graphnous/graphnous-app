"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/**
 * What the last answered request returned, and for which path.
 */
type Loaded<T> = {
    path: string;
    data: T | null;
    error: Error | null;
};

export type UseFetchOptions = {
    /**
     * A 404 is no data rather than an error, for something that may not
     * exist yet.
     */
    notFoundAsEmpty?: boolean;
};

/**
 * Loads JSON from the path, and again when it changes; no path, nothing.
 * A response for a path that is no longer asked for is ignored. refresh
 * loads it again, and resolves once it has; after an error, the data last
 * loaded for the path stays.
 *
 * @param failure what to say when the request fails
 */
export function useFetch<T>(path: string | null, failure: string, { notFoundAsEmpty = false }: UseFetchOptions = {}) {
    const [loaded, setLoaded] = useState<Loaded<T> | null>(null);

    // The refreshes still running
    const [refreshing, setRefreshing] = useState(0);

    // The path asked for now, to ignore refreshes of an earlier one
    const latest = useRef(path);

    useEffect(() => {
        latest.current = path;
    }, [path]);

    const failed = useCallback((error: unknown) => (last: Loaded<T> | null): Loaded<T> | null =>
        path === null ? last : {
            path,
            data: last?.path === path ? last.data : null,
            error: error instanceof Error ? error : new Error(failure),
        }, [path, failure]);

    useEffect(() => {
        if (!path) {
            return;
        }

        let current = true;

        fetchJson<T>(path, failure, notFoundAsEmpty).then(
            (data) => current && setLoaded({ path, data, error: null }),
            (error: unknown) => current && setLoaded(failed(error)),
        );

        return () => {
            current = false;
        };
    }, [path, failure, notFoundAsEmpty, failed]);

    const refresh = useCallback(async () => {
        if (!path) {
            return;
        }

        setRefreshing((count) => count + 1);

        try {
            const data = await fetchJson<T>(path, failure, notFoundAsEmpty);

            if (latest.current === path) {
                setLoaded({ path, data, error: null });
            }
        } catch (error) {
            if (latest.current === path) {
                setLoaded(failed(error));
            }
        } finally {
            setRefreshing((count) => count - 1);
        }
    }, [path, failure, notFoundAsEmpty, failed]);

    const current = path !== null && loaded?.path === path ? loaded : null;

    return {
        data: current?.data ?? null,
        error: current?.error ?? null,
        loading: path !== null && (current === null || refreshing > 0),
        refresh,
    };
}

async function fetchJson<T>(path: string, failure: string, notFoundAsEmpty: boolean): Promise<T | null> {
    const response = await fetch(path);

    if (notFoundAsEmpty && response.status === 404) {
        return null;
    }

    if (!response.ok) {
        throw new Error(failure);
    }

    return response.json();
}
