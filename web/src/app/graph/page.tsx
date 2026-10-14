"use client";

import {
    Alert,
    Container,
    PageHeader,
    Stack,
} from "@graphnous/theme";

import { SystemGraph } from "@/components/SystemGraph/SystemGraph";
import { useListProjects } from "@/lib/hooks/project/useListProjects";
import { useListSystemScans } from "@/lib/hooks/scan/useListSystemScans";
import { useSystemStore } from "@/lib/store/systemStore";
import { breadcrumbs } from "@/lib/breadcrumbs";

export default function GraphPage() {
    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    const { projects, error: projectsError } = useListProjects();

    // The newest scans of the system, as many as a page holds
    const { scans, error: scansError } = useListSystemScans({ systemId: selectedSystem?.id, size: 100 });

    const error = projectsError ?? scansError;

    return (
        <Container>
            <Stack gap={8}>
                <PageHeader
                    title="Graph"
                    breadcrumbs={breadcrumbs(selectedSystem, { label: "Graph" })}
                    description={`Explore the architecture and relationships across all repositories in ${selectedSystem?.name}.`}
                />
                {error && (
                    <Alert tone="error" title="The system's scans could not be loaded" announce>
                        {error.message}
                    </Alert>
                )}
                {/* Starts over for another system, as its choices are of its own projects */}
                <SystemGraph key={selectedSystem?.id} systemId={selectedSystem?.id} projects={projects} scans={scans} />
            </Stack>
        </Container>
    );
}
