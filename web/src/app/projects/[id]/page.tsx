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
import { useListScans } from "@/lib/hooks/scan/useListScans";
import { ScansTable } from "@/components/ScansTable/ScansTable";
import { CreateScanModal, CreateScanModalRef } from "@/components/CreateScanModal/CreateScanModal";
import { Scan } from "@/types";
import { StatsCard } from "@/components/StatsCard/StatsCard";
import { ProjectGraph } from "@/components/ProjectGraph/ProjectGraph";

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

    const createScanModalRef = useRef<CreateScanModalRef>(null);


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
                    breadcrumbs={[
                        {
                            label: selectedSystem?.name ?? "",
                            href: "/",
                        },
                        {
                            label: "Projects",
                            href: "/projects",
                        },
                        {
                            label: project.name,
                        },
                    ]}
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
                            <StatsCard className="flex-1" title="Files" stat="2800" />
                            <StatsCard className="flex-1" title="Classes" stat="2800" />
                            <StatsCard className="flex-1" title="Methods" stat="2800" />
                            <StatsCard className="flex-1" title="Dependencies" stat="2800" />
                        </section>
                        <section className="flex gap-4 flex-row mb-8">
                            <Card className="flex-1">
                                <CardHeader title="Languages" description="Composition of this project" />
                                <CardBody>
                                    None
                                </CardBody>
                            </Card>
                            <Card className="flex-1">
                                <CardHeader title="Changes since previous scan" description="Snapshot #41 → Snapshot #42" />
                                <CardBody>
                                    None
                                </CardBody>
                            </Card>
                        </section>
                    </TabPanel>
                    <TabPanel value={"code"}>
                        Code
                    </TabPanel>
                    <TabPanel value={"graph"}>
                        <ProjectGraph scans={scansPage.content} />
                    </TabPanel>
                    <TabPanel value={"dependencies"}>
                        <Text size="lg" as="div">
                            Dependencies
                        </Text>
                        <Text size="sm" as="div">
                            Explore internal and external dependencies in this project.
                        </Text>
                        <section className="flex gap-4 flex-row mb-8">
                            <StatsCard className="flex-1" title="" stat={27}></StatsCard>
                            <StatsCard className="flex-1" title="" stat={14}></StatsCard>
                            <StatsCard className="flex-1" title="" stat={6}></StatsCard>
                            <StatsCard className="flex-1" title="" stat={3}></StatsCard>
                        </section>
                        <section className="flex gap-4 flex-row mb-8">
                            <div className="flex-2">
                                <Card className="mb-2">
                                    <CardHeader title="Internal Dependencies" />
                                    <CardBody>
                                        None
                                    </CardBody>
                                </Card>
                                <Card className="mb-2">
                                    <CardHeader title="External dependencies" />
                                    <CardBody>
                                        None
                                    </CardBody>
                                </Card>
                                <Card className="mb-2">
                                    <CardHeader title="Changes since Scan #41" />
                                    <CardBody>
                                        None
                                    </CardBody>
                                </Card>
                            </div>
                            <div className="flex-1"></div>
                        </section>
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