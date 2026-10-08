"use client";

import { Capabilities } from "@/types/capabilities";
import { useFetch } from "@/lib/hooks/useFetch";

// Until they are loaded, nothing is enabled
const NONE: Capabilities = {
    capabilities: [],
    authorization: {
        enabled: false
    }
};

export function useGetCapabilities() {
    const { data, loading, error, refresh } = useFetch<Capabilities>("/api/capabilities", "Failed to load capabilities");

    return {
        capabilities: data ?? NONE,
        loading,
        error,
        refresh,
    };
}
