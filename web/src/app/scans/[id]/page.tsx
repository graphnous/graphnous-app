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
import { useParams } from "next/navigation";
import { useState } from "react";

import { useEventStream } from "@/lib/hooks";
import { useGetProject } from "@/lib/hooks/project/useGetProject";
import { useGetScan } from "@/lib/hooks/scan/useGetScan";
import { useGetScanSteps } from "@/lib/hooks/scan/steps/useGetScanSteps";
import { useGetScanLogs } from "@/lib/hooks/scan/logs/useGetScanLogs";
import { useSystemStore } from "@/lib/store/systemStore";
import { ScanLog } from "@/types";

function getLogLevel(level: string) {
    switch (level) {
        case "TRACE":
            return "TRACE";
        case "DEBUG":
            return "DEBUG";
        case "INFO":
            return "INFO";
        case "WARN":
            return "WARN";
        case "ERROR":
            return "ERROR";
        default:
            return "INFO";
    }
}

function getStepStatus(status: string) {
    switch (status) {
        case "PENDING":
            return "pending";
        case "RUNNING":
            return "running";
        case "COMPLETED":
            return "completed";
        case "FAILED":
            return "failed";
        case "SKIPPED":
            return "skipped";
        default:
            return "running";
    }
}

export default function ScanPage() {
    const { id } = useParams<{
        id: string;
    }>();

    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    const {
        scan,
        loading: scanLoading,
        error: scanError,
    } = useGetScan({
        scanId: id,
    });

    const {
        project,
        loading: projectLoading,
        error: projectError,
    } = useGetProject(
        scan?.projectId ?? "",
    );

    const { steps } = useGetScanSteps(
        id,
        true,
    );

    const { scanLogs } = useGetScanLogs(id);

    const [streamedLogs, setStreamedLogs] = useState<ScanLog[]>([]);

    useEventStream(
        scan?.projectId
            ? `/api/projects/${scan.projectId}/scans/${id}/logs/stream`
            : "",
        {
            event: "scanLog",
            enabled: Boolean(
                scan &&
                !scan.completedAt,
            ),
            onEvent: (event: ScanLog) => {
                setStreamedLogs((current) => [
                    ...current,
                    event,
                ]);
            },
        },
    );

    if (scanLoading) {
        return (
            <Container>
                <Text>Loading scan...</Text>
            </Container>
        );
    }

    if (scanError) {
        return (
            <Container>
                <Text>
                    {scanError.message}
                </Text>
            </Container>
        );
    }

    if (!scan) {
        return (
            <Container>
                <Text>
                    Scan not found.
                </Text>
            </Container>
        );
    }

    if (projectLoading) {
        return (
            <Container>
                <Text>
                    Loading project...
                </Text>
            </Container>
        );
    }

    if (projectError) {
        return (
            <Container>
                <Text>
                    {projectError.message}
                </Text>
            </Container>
        );
    }

    if (!project) {
        return (
            <Container>
                <Text>
                    Project not found.
                </Text>
            </Container>
        );
    }

    const logs = scan.completedAt
        ? scanLogs
        : [
            ...scanLogs,
            ...streamedLogs,
        ];

    return (
        <Container>
            <Stack gap={8}>
                <PageHeader
                    title={`Scan ${scan.id}`}
                    breadcrumbs={[
                        {
                            label:
                                selectedSystem?.name ??
                                "",
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

                <Inline
                    justify="between"
                    align="start"
                >
                    <Section
                        title="Logs"
                        className="w-full"
                    >
                        <Card>
                            <CardBody>
                                <Stack gap={4}>
                                    <LogViewer
                                        label="Logs"
                                        lines={logs.map(
                                            (log) => ({
                                                message:
                                                    log.message,
                                                level:
                                                    getLogLevel(
                                                        log.level,
                                                    ),
                                            }),
                                        )}
                                    />
                                </Stack>
                            </CardBody>
                        </Card>
                    </Section>

                    <Section
                        title="Execution"
                        className="w-full"
                    >
                        <Card>
                            <CardBody>
                                <Stack gap={4}>
                                    <Timeline
                                        label="Scan"
                                        steps={
                                            steps.map(
                                                (step) => ({
                                                    id: step.type,
                                                    title: (
                                                        <Text>
                                                            {
                                                                step.type
                                                            }
                                                        </Text>
                                                    ),
                                                    status:
                                                        getStepStatus(
                                                            step.status,
                                                        ),
                                                    meta: (
                                                        <Duration
                                                            start={
                                                                step.startedAt
                                                            }
                                                            end={
                                                                step.finishedAt
                                                            }
                                                        />
                                                    ),
                                                }),
                                            ) ?? []
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
