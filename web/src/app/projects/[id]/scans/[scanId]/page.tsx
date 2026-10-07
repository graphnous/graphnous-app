"use client";

import {
    Card,
    CardBody,
    Container,
    Duration,
    Inline,
    LogViewer,
    PageHeader,
    Section,
    Stack,
    Text,
    Timeline,
} from "@graphnous/theme";

import { PlayIcon } from "@phosphor-icons/react";
import { useRef, useState } from "react";
import { useParams } from "next/navigation";

import { useSystemStore } from "@/lib/store/systemStore";
import { useGetProject } from "@/lib/hooks/project/useGetProject";
import { ScanLog } from "@/types";
import { useGetScanSteps } from "@/lib/hooks/scan/steps/useGetScanSteps";
import { useGetScanLogs } from "@/lib/hooks/scan/logs/useGetScanLogs";
import { useEventStream } from "@/lib/hooks";
import { useGetScan } from "@/lib/hooks/scan/useGetScan";
import { useListScans } from "@/lib/hooks/scan/useListScans";

export default function ProjectPage() {

    const {
        id: projectId,
        scanId,
    } = useParams<{
        id: string;
        scanId: string;
    }>();

    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    const {
        project,
        loading: projectLoading,
        error: projectError,
    } = useGetProject(projectId);

    const {
        scans,
        loading: scanLoading,
        error: scanError,
    } = useListScans(
        projectId,
    );

    const scan = scans.find((s) => s.id === scanId);

    const { steps } = useGetScanSteps(
        scanId,
        true
    );

    const { scanLogs } = useGetScanLogs(scanId);

    const [streamedLogs, setStreamedLogs] = useState<ScanLog[]>([]);

    useEventStream(
        `/api/projects/${projectId}/scans/${scanId}/logs/stream`,
        {
            event: "scanLog",
            enabled: !scan?.completedAt,
            onEvent: (evt: ScanLog) => {
                setStreamedLogs((current) => [...current, evt]);
            },
        },
    );

    if (projectLoading || scanLoading) {
        return (
            <Container>
                <Text>Loading...</Text>
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

    if (scanError) {
        return (
            <Container>
                <Text>{scanError.message}</Text>
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

    if (!scan) {
        return (
            <Container>
                <Text>Scan not found.</Text>
            </Container>
        );
    }

    const logs = scan.completedAt
        ? scanLogs
        : streamedLogs;


    return (
        <Container>
            <Stack gap={8}>
                <PageHeader
                    title={`Scan ${scan.id}`}
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
                />

                <Inline justify="between" align="start">
                    <Section title={"Logs"} className="w-full">
                        <Card>
                            <CardBody>
                                <Stack gap={4}>
                                    <LogViewer
                                        label={"Logs"}
                                        lines={logs.map((log) => {
                                            return {
                                                message: log.message,
                                                level: log.level === 'TRACE' ? 'TRACE'
                                                    : log.level === 'DEBUG' ? 'DEBUG'
                                                        : log.level === 'INFO' ? 'INFO'
                                                            : log.level === 'WARN' ? 'WARN'
                                                                : log.level === 'ERROR' ? 'ERROR'
                                                                    : 'INFO'
                                            }
                                        })} />
                                </Stack>
                            </CardBody>
                        </Card>
                    </Section>
                    <Section title="Execution" className="w-full">
                        <Card>
                            <CardBody>
                                <Stack gap={4}>
                                    <Timeline
                                        label="Scan"
                                        steps={
                                            steps.map((step) => ({
                                                id: step.type,
                                                title: <Text>{step.type}</Text>,
                                                status: step.status === 'PENDING' ? 'pending'
                                                    : step.status === 'RUNNING' ? 'running'
                                                        : step.status === 'COMPLETED' ? 'completed'
                                                            : step.status === 'FAILED' ? 'failed'
                                                                : step.status === 'SKIPPED' ? 'skipped'
                                                                    : 'running',
                                                meta: <Duration start={step.startedAt} end={step.finishedAt} />
                                            })) ?? []
                                        }
                                    />
                                </Stack>
                            </CardBody>
                        </Card>
                    </Section>
                </Inline>
            </Stack>
        </Container>
    );
}
