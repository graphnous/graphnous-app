"use client";

import { useCallback, useEffect, useState } from "react";

import { Page, type Project } from "@/types";
import { useSystemStore } from "@/lib/store/systemStore";
import { emptyPage } from "@/types/page";

export function useListProjects() {
    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    const [projects, setProjects] = useState<Project[]>([]);
    const [page, setPage] = useState<Page<Project>>(emptyPage());

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const load = useCallback(async () => {
        if (!selectedSystem) {
            setProjects([]);
            return;
        }

        try {
            setLoading(true);
            setError(null);

            const response = await fetch(
                `/api/systems/${selectedSystem.id}/projects`,
            );

            if (!response.ok) {
                throw new Error("Failed to load projects");
            }

            const p = await response.json();

            setPage(p);
            setProjects(p.content);
        } catch (error) {
            setError(
                error instanceof Error
                    ? error
                    : new Error("Failed to load projects"),
            );
        } finally {
            setLoading(false);
        }
    }, [selectedSystem]);

    useEffect(() => {
        load();
    }, [load]);

    return {
        projects,
        page,
        loading,
        error,
        refresh: load,
    };
}