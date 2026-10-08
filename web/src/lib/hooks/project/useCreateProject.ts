"use client";

import { useState, useCallback } from "react";
import type { Project, System } from "@/types";

export type CreateProjectRequest = {
    name: string;
    description?: string;
    gitUrl: string;
};

/**
 * Creates projects in the system; none can be created without one.
 */
export function useCreateProject({ systemId }: { systemId: string | null }) {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const createProject = useCallback(
        async (request: CreateProjectRequest): Promise<Project> => {
            if (!systemId) {
                throw new Error("No system selected");
            }

            setLoading(true);
            setError(null);

            try {
                const response = await fetch(`/api/systems/${systemId}/projects`, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify(request),
                });

                if (!response.ok) {
                    throw new Error("Failed to create project");
                }

                return await response.json();
            } catch (error) {
                const err =
                    error instanceof Error
                        ? error
                        : new Error("Failed to create project");

                setError(err);
                throw err;
            } finally {
                setLoading(false);
            }
        },
        [],
    );

    return {
        createProject,
        loading,
        error,
    };
}
