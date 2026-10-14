"use client";

import {
    Container,
    PageHeader,
    Stack,
} from "@graphnous/theme";

import { useSystemStore } from "@/lib/store/systemStore";
import { breadcrumbs } from "@/lib/breadcrumbs";

export default function SettingsPage() {
    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    return (
        <Container>
            <Stack gap={8}>
                <PageHeader
                    title="Settings"
                    breadcrumbs={breadcrumbs(selectedSystem, { label: "Settings" })}
                    description={`Configure ${selectedSystem?.name || "the system"} and how Graphnous works for it.`}
                />
            </Stack>
        </Container>
    );
}
