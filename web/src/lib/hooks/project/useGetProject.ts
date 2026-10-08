"use client";

import type { Project } from "@/types";
import { useFetch } from "@/lib/hooks/useFetch";

export function useGetProject(id: string) {
    const { data, loading, error, refresh } = useFetch<Project>(
        id ? `/api/projects/${id}` : null,
        "Failed to load project",
    );

    return {
        project: data,
        loading,
        error,
        refresh,
    };
}
