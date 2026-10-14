"use client";

import {
    Button,
    Container,
    PageHeader,
    Section,
    Stack,
} from "@graphnous/theme";


import { useSystemStore } from "@/lib/store/systemStore";
import { useListProjects } from "@/lib/hooks/project/useListProjects";
import { ProjectsTable } from "@/components/ProjectsTable/ProjectsTable";
import { CreateProjectModal, CreateProjectModalRef } from "@/components/CreateProjectModal/CreateProjectModal";
import { useRef } from "react";
import { breadcrumbs } from "@/lib/breadcrumbs";

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
                    breadcrumbs={breadcrumbs(selectedSystem, { label: "Projects" })}
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
                    <ProjectsTable page={page} onProjectDeleted={() => refresh()} />
                </Section>
            </Stack>
        </Container>
    );
}