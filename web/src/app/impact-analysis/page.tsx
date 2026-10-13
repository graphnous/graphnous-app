"use client";

import {
    Container,
    PageHeader,
    Stack,
} from "@graphnous/theme";

import { useSearchParams } from "next/navigation";
import { Suspense } from "react";

import { ImpactAnalysis } from "@/components/ImpactAnalysis/ImpactAnalysis";
import { useListProjects } from "@/lib/hooks/project/useListProjects";
import { useSystemStore } from "@/lib/store/systemStore";
import { breadcrumbs } from "@/lib/breadcrumbs";

/**
 * Reads what a link asked for, ?scan=<scan id>&node=<node id>&name=<name>,
 * which needs a Suspense boundary around it when the page is prerendered.
 */
function LinkedImpactAnalysis({ systemId }: { systemId: string | undefined }) {
    const searchParams = useSearchParams();
    const { projects } = useListProjects();

    const scanId = searchParams.get("scan");
    const nodeId = searchParams.get("node");

    const initial = scanId && nodeId ? { scanId, nodeId, name: searchParams.get("name") } : null;

    return (
        // Starts over for another system, as its choices are of its own
        // projects, and for another link
        <ImpactAnalysis key={`${systemId}|${scanId}|${nodeId}`} projects={projects} initial={initial} />
    );
}

export default function ImpactAnalysisPage() {
    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    return (
        <Container>
            <Stack gap={8}>
                <PageHeader
                    title="Impact Analysis"
                    breadcrumbs={breadcrumbs(selectedSystem, { label: "Impact Analysis" })}
                    description={`Understand what could be affected when a component, API, or infrastructure resource changes.`}
                />
                <Suspense fallback={null}>
                    <LinkedImpactAnalysis systemId={selectedSystem?.id} />
                </Suspense>
            </Stack>
        </Container>
    );
}