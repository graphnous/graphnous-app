"use client";

import { Page, type Project } from "@/types";
import { useSystemStore } from "@/lib/store/systemStore";
import { emptyPage } from "@/types/page";
import { useFetch } from "@/lib/hooks/useFetch";

const NO_PROJECTS = emptyPage<Project>();

export function useListProjects() {
    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    const { data, loading, error, refresh } = useFetch<Page<Project>>(
        selectedSystem ? `/api/systems/${selectedSystem.id}/projects` : null,
        "Failed to load projects",
    );

    return {
        projects: data?.content ?? NO_PROJECTS.content,
        page: data ?? NO_PROJECTS,
        loading,
        error,
        refresh,
    };
}
