"use client";

import { useState, useCallback } from "react";
import type { System } from "@/types";

export type CreateSystemRequest = {
    name: string;
    description?: string;
};

export function useCreateSystem() {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const createSystem = useCallback(
        async (request: CreateSystemRequest): Promise<System> => {
            setLoading(true);
            setError(null);

            try {
                const response = await fetch("/api/systems", {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify(request),
                });

                if (!response.ok) {
                    throw new Error("Failed to create system");
                }

                return await response.json();
            } catch (error) {
                const err =
                    error instanceof Error
                        ? error
                        : new Error("Failed to create system");

                setError(err);
                throw err;
            } finally {
                setLoading(false);
            }
        },
        [],
    );

    return {
        createSystem,
        loading,
        error,
    };
}
