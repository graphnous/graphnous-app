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
                    title="Scans"
                    breadcrumbs={[
                        { label: selectedSystem?.name || '', href: "/" },
                        { label: "Scans" },
                    ]}
                    description={`Repository scanning activity across the ${selectedSystem?.name} system.`}
                />
            </Stack>
        </Container>
    );
}