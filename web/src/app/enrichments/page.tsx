"use client";

import {
    Container,
    PageHeader,
    Stack,
} from "@graphnous/theme";

import { useSystemStore } from "@/lib/store/systemStore";
import { breadcrumbs } from "@/lib/breadcrumbs";

export default function ProjectsPage() {
    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    return (
        <Container>
            <Stack gap={8}>
                <PageHeader
                    title="System Enrichments"
                    breadcrumbs={breadcrumbs(selectedSystem, { label: "Enrichments" })}
                    description={`Connect specific project scans to build a unified view of your system.`}
                />
            </Stack>
        </Container>
    );
}