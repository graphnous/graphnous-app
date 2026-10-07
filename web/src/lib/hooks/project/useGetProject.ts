"use client";

import { useCallback, useEffect, useState } from "react";

import type { Project } from "@/types";

export function useGetProject(id: string) {
    const [project, setProject] = useState<Project | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const load = useCallback(async () => {
        if (!id) {
            setProject(null);
            setError(null);
            return;
        }

        try {
            setLoading(true);
            setError(null);

            const response = await fetch(`/api/projects/${id}`);

            if (!response.ok) {
                throw new Error("Failed to load project");
            }

            const project: Project = await response.json();

            setProject(project);
        } catch (error) {
            setError(
                error instanceof Error
                    ? error
                    : new Error("Failed to load project"),
            );
        } finally {
            setLoading(false);
        }
    }, [id]);

    useEffect(() => {
        load();
    }, [load]);

    return {
        project,
        loading,
        error,
        refresh: load,
    };
}