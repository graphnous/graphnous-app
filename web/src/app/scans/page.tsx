"use client";

import {
    Card,
    CardBody,
    Container,
    PageHeader,
    Section,
    Stack,
    Text,
} from "@graphnous/theme";

import { useSystemStore } from "@/lib/store/systemStore";
import { useListSystemScans } from "@/lib/hooks/scan/useListSystemScans";
import { useListProjects } from "@/lib/hooks/project/useListProjects";
import { ScansTable } from "@/components/ScansTable/ScansTable";
import { breadcrumbs } from "@/lib/breadcrumbs";

export default function ScanOverviewPage() {
    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    const { page, error, refresh } = useListSystemScans({
        systemId: selectedSystem?.id,
    });

    // To name each scan's project; of the selected system, as the scans
    const { projects } = useListProjects();

    return (
        <Container>
            <Stack gap={8}>
                <PageHeader
                    title="Scans"
                    breadcrumbs={breadcrumbs(selectedSystem, { label: "Scans" })}
                    description={`Repository scanning activity across the ${selectedSystem?.name} system.`}
                />

                <Section title="Scans">
                    <Card>
                        <CardBody>
                            {error ? (
                                <Text>{error.message}</Text>
                            ) : (
                                <ScansTable page={page} projects={projects} onScanDeleted={() => refresh()} />
                            )}
                        </CardBody>
                    </Card>
                </Section>
            </Stack>
        </Container>
    );
}
