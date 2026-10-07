"use client";

import { useCallback, useEffect, useState } from "react";
import { Capabilities } from "@/types/capabilities";

export function useGetCapabilities() {
    const [capabilities, setCapabilities] = useState<Capabilities>({
        capabilities: [],
        authorization: {
            enabled: false
        }
    });

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<Error | null>(null);

    const load = useCallback(async () => {
        try {
            setLoading(true);
            setError(null);

            const response = await fetch("/api/capabilities");

            if (!response.ok) {
                throw new Error("Failed to load capabilities");
            }

            const res = await response.json();

            setCapabilities(res);
        } catch (error) {
            setError(
                error instanceof Error
                    ? error
                    : new Error("Failed to load capabilities"),
            );
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    return {
        capabilities,
        loading,
        error,
        refresh: load,
    };
}