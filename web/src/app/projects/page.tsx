"use client";

import {
    Badge,
    Button,
    Card,
    CardBody,
    CardHeader,
    Container,
    PageHeader,
    Section,
    Stack,
    Text,
} from "@graphnous/theme";

import { PlayIcon } from "@phosphor-icons/react";

import { useSystemStore } from "@/lib/store/systemStore";
import { useListProjects } from "@/lib/hooks/project/useListProjects";
import { ProjectsTable } from "@/components/ProjectsTable/ProjectsTable";
import { CreateProjectModal, CreateProjectModalRef } from "@/components/CreateProjectModal/CreateProjectModal";
import { useRef } from "react";
import { ScansTable } from "@/components/ScansTable/ScansTable";
import { useListScans } from "@/lib/hooks/scan/useListScans";

export default function ProjectsPage() {
    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );


    const { page, refresh } = useListProjects()

    const createProjectModalRef = useRef<CreateProjectModalRef>(null);

    return (
        <Container>
            <Stack gap={8}>
                <PageHeader
                    title="Projects"
                    breadcrumbs={[
                        { label: selectedSystem?.name || '', href: "/" },
                        { label: "Projects" },
                    ]}
                    description={`Projects that make up the ${selectedSystem?.name} system.`}
                    actions={
                        <Button
                            onClick={() => createProjectModalRef.current?.open()}
                        >
                            Create Project
                        </Button>
                    }
                />

                <CreateProjectModal ref={createProjectModalRef} onCreated={() => refresh()} />

                <Section title="Projects">
                    <Card>
                        <CardBody>
                            <ProjectsTable page={page} onProjectDeleted={() => refresh()} />
                        </CardBody>
                    </Card>
                </Section>
            </Stack>
        </Container>
    );
}