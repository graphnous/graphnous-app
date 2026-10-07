"use client";

import {
    Container,
    PageHeader,
    Stack,
} from "@graphnous/theme";

import { useSystemStore } from "@/lib/store/systemStore";

export default function ProjectsPage() {
    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    return (
        <Container>
            <Stack gap={8}>
                <PageHeader
                    title="System Enrichments"
                    breadcrumbs={[
                        { label: selectedSystem?.name || '', href: "/" },
                        { label: "Enrichments" },
                    ]}
                    description={`Connect specific project scans to build a unified view of your system.`}
                />
            </Stack>
        </Container>
    );
}