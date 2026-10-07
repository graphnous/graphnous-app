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
                    title="Impact Analysis"
                    breadcrumbs={[
                        { label: selectedSystem?.name || '', href: "/" },
                        { label: "Impact Analysis" },
                    ]}
                    description={`Understand what could be affected when a component, API, or infrastructure resource changes.`}
                />
            </Stack>
        </Container>
    );
}