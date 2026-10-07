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
import { StatsCard } from "@/components/StatsCard/StatsCard";

export default function Home() {
  const selectedSystem = useSystemStore(
    (state) => state.selectedSystem,
  );

  const { page, refresh } = useListProjects()

  const createProjectModalRef = useRef<CreateProjectModalRef>(null);

  return (
    <Container>

      <Stack gap={8}>
        <PageHeader
          title={selectedSystem?.name}
          breadcrumbs={[
            { label: selectedSystem?.name || '', href: "/" },
            { label: "Overview" },
          ]}
        />

        <section className="flex gap-4 flex-row">
          <StatsCard className="flex-1" title="Projects" stat={page.totalElements} />
          <StatsCard className="flex-1" title="Modules" stat={120} />
          <StatsCard className="flex-1" title="Files" stat={4812} />
          <StatsCard className="flex-1" title="Classes" stat={1441} />
          <StatsCard className="flex-1" title="Methods" stat={2875} />
        </section>
        <Section title="Projects">
          <ProjectsTable
            page={page}
            onProjectDeleted={() => refresh()} />
        </Section>
        <section className="flex gap-4 flex-row">
          <Card className="flex-1">
            <CardHeader title="Project Activity"></CardHeader>
          </Card>
          <Card className="flex-1">
            <CardHeader title="Graph Entry Points"></CardHeader>
          </Card>
        </section>
      </Stack>
    </Container>
  );
}