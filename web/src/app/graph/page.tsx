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
                    title="Graph"
                    breadcrumbs={[
                        { label: selectedSystem?.name || '', href: "/" },
                        { label: "System Graph" },
                    ]}
                    description={`Explore the architecture and relationships across all repositories in ${selectedSystem?.name}.`}
                />
            </Stack>
        </Container>
    );
}