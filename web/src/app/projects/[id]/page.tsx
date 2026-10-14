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
    Tab,
    TabList,
    TabPanel,
    Tabs,
    Text,
} from "@graphnous/theme";

import { PlayIcon } from "@phosphor-icons/react";
import { useRef } from "react";
import { useParams, useRouter } from "next/navigation";

import { useSystemStore } from "@/lib/store/systemStore";
import { useGetProject } from "@/lib/hooks/project/useGetProject";
import { useProjectStats } from "@/lib/hooks/project/useProjectStats";
import { useListScans } from "@/lib/hooks/scan/useListScans";
import { useDependencies } from "@/lib/hooks/scan/dependencies/useDependencies";
import { ScanTimeline } from "@/components/ScanTimeline/ScanTimeline";
import { ScansTable } from "@/components/ScansTable/ScansTable";
import { CreateScanModal, CreateScanModalRef } from "@/components/CreateScanModal/CreateScanModal";
import { Scan } from "@/types";
import { StatsCard } from "@/components/StatsCard/StatsCard";
import { LanguagesChart } from "@/components/LanguagesChart/LanguagesChart";
import { breadcrumbs } from "@/lib/breadcrumbs";
import { ProjectCode } from "@/components/ProjectCode/ProjectCode";
import { ProjectDependencies } from "@/components/ProjectDependencies/ProjectDependencies";
import { ProjectGraph } from "@/components/ProjectGraph/ProjectGraph";
import { LatestScanChanges, ProjectScanComparison } from "@/components/ScanComparison/ScanComparison";

export default function ProjectPage() {
    const { id } = useParams<{ id: string }>();
    const router = useRouter();

    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    const {
        project,
        loading: projectLoading,
        error: projectError,
    } = useGetProject(id);

    const {
        page: scansPage,
        refresh: refreshScans,
    } = useListScans(id);

    const {
        stats,
        loading: statsLoading,
        error: statsError,
    } = useProjectStats(id);

    // The stats have no dependencies; only the count of the same scan's is needed
    const {
        page: dependenciesPage,
        loading: dependenciesLoading,
    } = useDependencies({ scanId: stats?.scanId, size: 1 });

    const createScanModalRef = useRef<CreateScanModalRef>(null);

    const languages = Object.entries(stats?.languages ?? {})
        .map(([language, files]) => ({ language, files: files.length }));

    const numberOfFiles = languages.reduce((total, { files }) => total + files, 0);

    /**
     * The stat as shown: a dash while loading, or without a scan with stats.
     */
    const stat = (value: number | undefined, loading = statsLoading) =>
        loading || value === undefined ? "–" : value.toLocaleString();

    if (projectLoading) {
        return (
            <Container>
                <Text>Loading project...</Text>
            </Container>
        );
    }

    if (projectError) {
        return (
            <Container>
                <Text>{projectError.message}</Text>
            </Container>
        );
    }

    if (!project) {
        return (
            <Container>
                <Text>Project not found.</Text>
            </Container>
        );
    }

    const scanCreated = (scan: Scan) => {
        refreshScans()

        router.push(`/projects/${id}/scans/${scan.id}`)
    }

    return (
        <Container>
            <Stack gap={8}>
                <PageHeader
                    title={project.name}
                    breadcrumbs={breadcrumbs(
                        selectedSystem,
                        { label: "Projects", href: "/projects" },
                        { label: project.name },
                    )}
                    actions={
                        <Button
                            icon={PlayIcon}
                            onClick={() => createScanModalRef.current?.open()}
                        >
                            Scan now
                        </Button>
                    }
                />

                <CreateScanModal ref={createScanModalRef} onCreated={(scan) => scanCreated(scan)} projectId={project.id} />

                <Tabs defaultValue={"overview"}>
                    <TabList label={"Project"}>
                        <Tab value={"overview"}>Overview</Tab>
                        <Tab value={"code"}>Code</Tab>
                        <Tab value={"graph"}>Graph</Tab>
                        <Tab value={"history"}>History</Tab>
                        <Tab value={"compare"}>Compare</Tab>
                        <Tab value={"dependencies"}>Dependencies</Tab>
                        <Tab value={"impact"}>Impact</Tab>
                        <Tab value={"chat"}>Chat</Tab>
                        <Tab value={"scans"}>Scans</Tab>
                    </TabList>
                    <TabPanel value={"overview"}>
                        <section className="flex gap-4 flex-row mb-8">
                            <Card className="flex-1">
                                <CardHeader title="Latest Scan" />
                            </Card>
                            <Card className="flex-1">
                                <CardHeader title="Architecture" />
                            </Card>
                        </section>
                        <section className="flex gap-4 flex-row mb-8">
                            <StatsCard className="flex-1" title="Files" stat={stat(stats ? numberOfFiles : undefined)} />
                            <StatsCard className="flex-1" title="Classes" stat={stat(stats?.numberOfClassesParsed)} />
                            <StatsCard className="flex-1" title="Methods" stat={stat(stats?.numberOfMethodsParsed)} />
                            <StatsCard
                                className="flex-1"
                                title="Dependencies"
                                stat={stat(stats ? dependenciesPage.totalElements : undefined, statsLoading || dependenciesLoading)}
                            />
                        </section>
                        {statsError && (
                            <Text className="mb-8">{statsError.message}</Text>
                        )}
                        <section className="flex gap-4 flex-row mb-8">
                            <Card className="flex-1">
                                <CardHeader title="Languages" description="Composition of this project" />
                                <CardBody>
                                    <LanguagesChart
                                        languages={Object.fromEntries(languages.map(({ language, files }) => [language, files]))}
                                    />
                                </CardBody>
                            </Card>
                            <Card className="flex-1">
                                <CardHeader title="Changes since previous scan" description="The latest completed scan against the one before it" />
                                <CardBody>
                                    <LatestScanChanges scans={scansPage.content} />
                                </CardBody>
                            </Card>
                        </section>
                    </TabPanel>
                    <TabPanel value={"code"}>
                        <ProjectCode scans={scansPage.content} />
                    </TabPanel>
                    <TabPanel value={"graph"}>
                        <ProjectGraph scans={scansPage.content} />
                    </TabPanel>
                    <TabPanel value={"history"}>
                        <ScanTimeline scans={scansPage.content} />
                    </TabPanel>
                    <TabPanel value={"compare"}>
                        <ProjectScanComparison scans={scansPage.content} />
                    </TabPanel>
                    <TabPanel value={"dependencies"}>
                        <ProjectDependencies scans={scansPage.content} />
                    </TabPanel>
                    <TabPanel value={"impact"}>
                        Impact
                    </TabPanel>
                    <TabPanel value={"chat"}>
                        Chat
                    </TabPanel>
                    <TabPanel value={"scans"}>
                        <ScansTable page={scansPage} onScanDeleted={() => refreshScans()} />
                    </TabPanel>
                </Tabs>
            </Stack>
        </Container>
    );
}