"use client";

import {
    Alert,
    Badge,
    Button,
    Card,
    CardBody,
    CardHeader,
    Code,
    Container,
    CopyButton,
    DescriptionList,
    Duration,
    EmptyState,
    Link,
    LogViewer,
    PageHeader,
    Stack,
    Tab,
    TabList,
    TabPanel,
    Tabs,
    Text,
    Timeline,
    Timestamp,
} from "@graphnous/theme";
import { GraphIcon, SparkleIcon } from "@phosphor-icons/react";
import { useEffect, useState } from "react";

import { EnhanceScanModal } from "@/components/EnhanceScanModal/EnhanceScanModal";
import { breadcrumbs } from "@/lib/breadcrumbs";
import { ProjectCode } from "@/components/ProjectCode/ProjectCode";
import { ProjectDependencies } from "@/components/ProjectDependencies/ProjectDependencies";
import { ProjectGraph } from "@/components/ProjectGraph/ProjectGraph";
import { revisionLabel, ScanRevision } from "@/components/ScanRevision/ScanRevision";
import { ScanStatusBadge } from "@/components/ScanStatusBadge/ScanStatusBadge";
import { useEventStream } from "@/lib/hooks";
import { useGetProject } from "@/lib/hooks/project/useGetProject";
import { languageLabel } from "@/lib/languages";
import { useScanOptions } from "@/lib/hooks/scan/useScanOptions";
import { useGetScanGraph } from "@/lib/hooks/scan/graph/useGetScanGraph";
import { useGetScanLogs } from "@/lib/hooks/scan/logs/useGetScanLogs";
import { useGetScanSteps } from "@/lib/hooks/scan/steps/useGetScanSteps";
import { useGetScan } from "@/lib/hooks/scan/useGetScan";
import { useListScans } from "@/lib/hooks/scan/useListScans";
import { useSystemStore } from "@/lib/store/systemStore";
import type { ScanLog, ScanStatus, ScanStepStatus, ScanStepType } from "@/types";

/**
 * Statuses of a scan that is still running, and may still change.
 */
const ACTIVE: ScanStatus[] = ["PENDING", "QUEUED", "RUNNING"];

const STEPS: Record<ScanStepType, string> = {
    CHECKOUT: "Check out the repository",
    PLAN: "Plan what to scan",
    SCAN: "Scan the targets",
    STORE: "Store the results",
    ENHANCE_RESULTS: "Enhance the results",
    ENHANCE_SCAN: "Enhance the scan",
};

const STEP_STATUSES: Record<ScanStepStatus, "pending" | "running" | "completed" | "failed" | "skipped"> = {
    PENDING: "pending",
    RUNNING: "running",
    COMPLETED: "completed",
    FAILED: "failed",
    SKIPPED: "skipped",
};

const LOG_LEVELS = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"] as const;

function logLevel(level: string): (typeof LOG_LEVELS)[number] {
    return LOG_LEVELS.find((known) => known === level) ?? "INFO";
}

function NotYet() {
    return <span aria-label="Not yet">–</span>;
}

export type ScanDetailsProps = {
    scanId: string;
    /**
     * How the page was reached, which its trail follows: through the
     * system's scans, or through the scan's project.
     */
    via?: "scans" | "project";
};

/**
 * Everything about one scan: what it is of and how it went, its steps and
 * logs as it runs, and, once it completed, its graph and dependencies. A
 * completed scan can be enhanced again from here.
 */
export function ScanDetails({ scanId, via = "scans" }: ScanDetailsProps) {
    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    const {
        scan,
        loading: scanLoading,
        error: scanError,
        refresh: refreshScan,
    } = useGetScan({ scanId });

    const {
        project,
        error: projectError,
    } = useGetProject(scan?.projectId ?? "");

    // The project's other scans, to compare this one with the one before it
    const { scans } = useListScans(scan?.projectId ?? "");

    const { steps, refresh: refreshSteps } = useGetScanSteps(scanId, true);

    const { scanLogs, refresh: refreshLogs } = useGetScanLogs(scanId);

    const active = scan ? ACTIVE.includes(scan.status) : false;
    const completed = scan?.status === "COMPLETED";

    // Followed while it runs, to show when it completes or fails
    useEffect(() => {
        if (!active) {
            return;
        }

        const interval = setInterval(refreshScan, 2000);

        return () => clearInterval(interval);
    }, [active, refreshScan]);

    const [streamedLogs, setStreamedLogs] = useState<ScanLog[]>([]);

    useEventStream(
        `/api/scans/${scanId}/logs/stream`,
        {
            event: "scanLog",
            enabled: active,
            onEvent: (event: ScanLog) => {
                setStreamedLogs((current) => [...current, event]);
            },
        },
    );

    const [choosingEnhancers, setChoosingEnhancers] = useState(false);

    // What was chosen when the scan was created, to choose again at first
    const { enhancers: chosenEnhancers, excludes, languages } = useScanOptions(scanId);

    const enhancing = completed && steps.some((step) => step.type === "ENHANCE_RESULTS" && step.status === "RUNNING");

    // The enhancers that ran are next to the scan in its graph
    const { graph: around, refresh: refreshEnhancers } = useGetScanGraph({ scanId: completed ? scanId : null, depth: 1 });

    const enhancers = (around?.nodes ?? [])
        .filter((node) => (node.type as string) === "Enhancement")
        .map((node) => ({ name: node.name ?? "", version: node.properties.version }));

    // Once the scan ends, or was enhanced again, its logs are loaded as a
    // whole: only those of a running scan are streamed
    useEffect(() => {
        if (!active && !enhancing) {
            refreshLogs();
            refreshEnhancers();
        }
    }, [active, enhancing, refreshLogs, refreshEnhancers]);

    if (scanLoading && !scan) {
        return (
            <Container>
                <Text>Loading scan...</Text>
            </Container>
        );
    }

    if (scanError && !scan) {
        return (
            <Container>
                <Alert tone="error" title="The scan could not be loaded" announce>
                    {scanError.message}
                </Alert>
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

    // Streamed logs may repeat those loaded before the stream opened
    const loaded = new Set(scanLogs.map((log) => log.id));
    const logs = active ? [...scanLogs, ...streamedLogs.filter((log) => !loaded.has(log.id))] : scanLogs;

    const failedStep = steps.find((step) => step.status === "FAILED");
    const projectName = project?.name ?? "Project";

    // What the scan is of, as the tables of scans call it: main · 4f2a9c1
    const scanLabel = `Scan ${[scan.branch, revisionLabel(scan)].filter(Boolean).join(" · ")}`;

    return (
        <Container>
            <Stack gap={8}>
                <PageHeader
                    title={`${projectName} · ${scan.branch}`}
                    breadcrumbs={
                        via === "project"
                            ? breadcrumbs(
                                selectedSystem,
                                { label: "Projects", href: "/projects" },
                                { label: projectName, href: `/projects/${scan.projectId}` },
                                { label: scanLabel },
                            )
                            : breadcrumbs(
                                selectedSystem,
                                { label: "Scans", href: "/scans" },
                                { label: scanLabel },
                            )
                    }
                    actions={
                        <Button
                            variant="secondary"
                            icon={SparkleIcon}
                            // Not disabled while enhancing: a step left running by a
                            // server that stopped is run again, and the API refuses
                            // a scan that really is being enhanced
                            disabled={!completed}
                            onClick={() => setChoosingEnhancers(true)}
                        >
                            {enhancing ? "Enhancing…" : "Enhance again"}
                        </Button>
                    }
                />

                {projectError && (
                    <Alert tone="warning" title="The scan's project could not be loaded">
                        {projectError.message}
                    </Alert>
                )}

                <EnhanceScanModal
                    scanId={scanId}
                    open={choosingEnhancers}
                    onClose={() => setChoosingEnhancers(false)}
                    chosen={chosenEnhancers}
                    previous={enhancers.map((enhancer) => enhancer.name)}
                    onStarted={refreshSteps}
                />

                {failedStep?.error && (
                    <Alert tone="error" title={`Failed to ${STEPS[failedStep.type].toLowerCase()}`}>
                        {failedStep.error}
                    </Alert>
                )}

                <Tabs defaultValue="overview">
                    <TabList label="Scan">
                        <Tab value="overview">Overview</Tab>
                        <Tab value="code" disabled={!completed}>Code</Tab>
                        <Tab value="graph" disabled={!completed}>Graph</Tab>
                        <Tab value="dependencies" disabled={!completed}>Dependencies</Tab>
                    </TabList>

                    <TabPanel value="overview">
                        <div className="flex flex-col gap-4 pt-4 lg:flex-row lg:items-start">
                            <div className="flex w-full shrink-0 flex-col gap-4 lg:w-96">
                                <Card>
                                    <CardHeader title="Scan" />
                                    <CardBody>
                                        <DescriptionList
                                            items={[
                                                { label: "Status", value: <ScanStatusBadge status={scan.status} /> },
                                                {
                                                    label: "Project",
                                                    value: <Link href={`/projects/${scan.projectId}`}>{projectName}</Link>,
                                                },
                                                { label: "Branch", value: <span className="font-medium">{scan.branch}</span> },
                                                {
                                                    label: "Revision",
                                                    value: (
                                                        <ScanRevision
                                                            revision={scan.revision}
                                                            requestedRevision={scan.requestedRevision}
                                                        />
                                                    ),
                                                },
                                                { label: "Created", value: <Timestamp value={scan.createdAt} /> },
                                                {
                                                    label: "Started",
                                                    value: scan.startedAt ? <Timestamp value={scan.startedAt} /> : <NotYet />,
                                                },
                                                {
                                                    label: "Completed",
                                                    value: scan.completedAt ? <Timestamp value={scan.completedAt} /> : <NotYet />,
                                                },
                                                {
                                                    label: "Took",
                                                    value: scan.startedAt
                                                        ? <Duration start={scan.startedAt} end={scan.completedAt} />
                                                        : <NotYet />,
                                                },
                                                {
                                                    label: "Enhancers",
                                                    value: enhancers.length > 0 ? (
                                                        <span className="flex flex-wrap gap-1">
                                                            {enhancers.map((enhancer) => (
                                                                <Badge key={enhancer.name}>
                                                                    {[enhancer.name, enhancer.version].filter(Boolean).join(" ")}
                                                                </Badge>
                                                            ))}
                                                        </span>
                                                    ) : (
                                                        <Text as="span" size="sm" tone="muted">
                                                            {completed ? "None" : "Once it completes"}
                                                        </Text>
                                                    ),
                                                },
                                                {
                                                    label: "Languages",
                                                    value: languages.length > 0 ? (
                                                        <Text as="span" size="sm">
                                                            {languages.map(languageLabel).join(", ")}
                                                        </Text>
                                                    ) : (
                                                        <Text as="span" size="sm" tone="muted">Every language found</Text>
                                                    ),
                                                },
                                                {
                                                    label: "Left out",
                                                    value: excludes.length > 0 ? (
                                                        <span className="flex flex-wrap gap-1">
                                                            {excludes.map((pattern) => (
                                                                <Code key={pattern} className="text-xs">{pattern}</Code>
                                                            ))}
                                                        </span>
                                                    ) : (
                                                        <Text as="span" size="sm" tone="muted">No files</Text>
                                                    ),
                                                },
                                                {
                                                    label: "Id",
                                                    value: (
                                                        <span className="flex items-center gap-1">
                                                            <Code className="min-w-0 truncate text-xs">{scan.id}</Code>
                                                            <CopyButton value={scan.id} label="Copy the scan's id" size="sm" />
                                                        </span>
                                                    ),
                                                },
                                            ]}
                                        />
                                    </CardBody>
                                </Card>

                                <Card>
                                    <CardHeader title="Execution" />
                                    <CardBody>
                                        <Timeline
                                            label="The steps of the scan"
                                            steps={steps.map((step) => ({
                                                id: step.type,
                                                // The timeline puts it in a paragraph of its own
                                                title: STEPS[step.type] ?? step.type,
                                                status: STEP_STATUSES[step.status] ?? "running",
                                                meta: <Duration start={step.startedAt} end={step.finishedAt} />,
                                            }))}
                                        />
                                    </CardBody>
                                </Card>
                            </div>

                            <Card className="min-w-0 flex-1">
                                <CardHeader title="Logs" description={active ? "Following the scan as it runs" : undefined} />
                                <CardBody>
                                    <LogViewer
                                        label="Logs"
                                        lines={logs.map((log) => ({
                                            timestamp: new Date(log.timestamp).toISOString(),
                                            message: log.message,
                                            level: logLevel(log.level),
                                        }))}
                                    />
                                </CardBody>
                            </Card>
                        </div>
                    </TabPanel>

                    <TabPanel value="code">
                        {completed ? (
                            <ProjectCode scans={scans} scanId={scan.id} />
                        ) : (
                            <NotCompleted what="code" />
                        )}
                    </TabPanel>

                    <TabPanel value="graph">
                        {completed ? (
                            <ProjectGraph scans={scans} scanId={scan.id} />
                        ) : (
                            <NotCompleted what="graph" />
                        )}
                    </TabPanel>

                    <TabPanel value="dependencies">
                        {completed ? (
                            <ProjectDependencies scans={scans} scanId={scan.id} />
                        ) : (
                            <NotCompleted what="dependencies" />
                        )}
                    </TabPanel>
                </Tabs>
            </Stack>
        </Container>
    );
}

function NotCompleted({ what }: { what: string }) {
    return (
        <EmptyState
            icon={GraphIcon}
            title="The scan has not completed"
            description={`The ${what} is what a scan found. Once the scan completes, it shows here.`}
        />
    );
}
