"use client";

import { useCallback, useEffect, useState } from "react";
import type { System } from "@/types";

export function useListSystems() {
    const [systems, setSystems] = useState<System[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<Error | null>(null);

    const load = useCallback(async () => {
        try {
            setLoading(true);
            setError(null);

            const response = await fetch("/api/systems");

            if (!response.ok) {
                throw new Error("Failed to load systems");
            }

            const page = await response.json();

            setSystems(page.content);
        } catch (error) {
            setError(
                error instanceof Error
                    ? error
                    : new Error("Failed to load systems"),
            );
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    return {
        systems,
        loading,
        error,
        refresh: load,
    };
}