"use client";

import {
    Container,
    EmptyState,
    PageHeader,
    Stack,
} from "@graphnous/theme";

import { HourglassIcon } from "@phosphor-icons/react";

import { useSystemStore } from "@/lib/store/systemStore";
import { breadcrumbs } from "@/lib/breadcrumbs";

export default function EnrichmentsPage() {
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
                <EmptyState
                    icon={HourglassIcon}
                    level={2}
                    title="Coming soon"
                    description="Enrichments are on their way: connecting the scans of projects into one view of the system."
                />
            </Stack>
        </Container>
    );
}