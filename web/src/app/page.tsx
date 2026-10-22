"use client";

import {
  Badge,
  Button,
  Card,
  CardBody,
  CardHeader,
  Container,
  EmptyState,
  PageHeader,
  Section,
  Stack,
  Text,
} from "@graphnous/theme";

import { HourglassIcon, PlayIcon } from "@phosphor-icons/react";

import { useSystemStore } from "@/lib/store/systemStore";
import { useListProjects } from "@/lib/hooks/project/useListProjects";
import { useSystemStats } from "@/lib/hooks/system/useSystemStats";
import { ProjectsTable } from "@/components/ProjectsTable/ProjectsTable";
import { CreateProjectModal, CreateProjectModalRef } from "@/components/CreateProjectModal/CreateProjectModal";
import { useRef } from "react";
import { StatsCard } from "@/components/StatsCard/StatsCard";
import { breadcrumbs } from "@/lib/breadcrumbs";

export default function Home() {
  const selectedSystem = useSystemStore(
    (state) => state.selectedSystem,
  );

  const { page, refresh } = useListProjects()

  const {
    stats,
    loading: statsLoading,
    error: statsError,
  } = useSystemStats(selectedSystem?.id);

  const numberOfFiles = Object.values(stats?.languages ?? {})
    .reduce((total, files) => total + files.length, 0);

  /**
   * The stat as shown: a dash while loading, or when the stats failed to load.
   */
  const stat = (value: number | undefined) =>
    statsLoading || value === undefined ? "–" : value.toLocaleString();

  const createProjectModalRef = useRef<CreateProjectModalRef>(null);

  return (
    <Container>

      <Stack gap={8}>
        <PageHeader
          title={selectedSystem?.name}
          breadcrumbs={breadcrumbs(selectedSystem, { label: "Overview" })}
        />

        <section className="flex gap-4 flex-row">
          <StatsCard className="flex-1" title="Projects" stat={stat(stats?.numberOfProjects)} />
          <StatsCard className="flex-1" title="Scans" stat={stat(stats?.numberOfScans)} />
          <StatsCard className="flex-1" title="Modules" stat={stat(stats?.numberOfModulesScanned)} />
          <StatsCard className="flex-1" title="Files" stat={stat(stats ? numberOfFiles : undefined)} />
        </section>
        {statsError && (
          <Text>{statsError.message}</Text>
        )}
        <Section title="Projects">
          <ProjectsTable
            page={page}
            onProjectDeleted={() => refresh()} />
        </Section>
        <section className="flex gap-4 flex-row">
          <Card className="flex-1">
            <CardHeader title="Project Activity"></CardHeader>
            <CardBody>
              <EmptyState
                icon={HourglassIcon}
                level={4}
                title="Coming soon"
                description="What happens in the system's projects, scan by scan."
              />
            </CardBody>
          </Card>
          <Card className="flex-1">
            <CardHeader title="Graph Entry Points"></CardHeader>
            <CardBody>
              <EmptyState
                icon={HourglassIcon}
                level={4}
                title="Coming soon"
                description="Where requests enter the system's graph."
              />
            </CardBody>
          </Card>
        </section>
      </Stack>
    </Container>
  );
}