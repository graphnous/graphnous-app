"use client";

import {
    Alert,
    Badge,
    Button,
    Card,
    CardBody,
    CardHeader,
    Code,
    EmptyState,
    Field,
    Pagination,
    SearchInput,
    Select,
    Spinner,
    Table,
    Text,
    type BadgeTone,
} from "@graphnous/theme";
import { CrosshairIcon, TargetIcon } from "@phosphor-icons/react";
import { useState } from "react";

import { signature } from "@/components/ProjectCode/ProjectCode";
import { completedScans, ScanSelector } from "@/components/ScanSelector/ScanSelector";
import { useScanCode } from "@/lib/hooks/scan/code/useScanCode";
import { useGetScan } from "@/lib/hooks/scan/useGetScan";
import { useListScans } from "@/lib/hooks/scan/useListScans";
import { useScanUsages } from "@/lib/hooks/scan/usages/useScanUsages";
import { argumentNames, passes, writtenArgument } from "@/lib/impact";
import type { Project, ScanClass, ScanMethod, ScanUsage, ScanUsageRelationship } from "@/types";

/**
 * What can be asked about: a class with its members, or one method or
 * function.
 */
type Kind = "classes" | "methods";

const KINDS = [
    { value: "classes", label: "Classes" },
    { value: "methods", label: "Methods and functions" },
];

const DEPTHS = [
    { value: "1", label: "What uses it" },
    { value: "2", label: "And what calls that" },
    { value: "3", label: "And what calls that too" },
];

/**
 * How each way of using something reads, and how much it stands out: what
 * has to change with it first.
 */
const RELATIONSHIPS: Record<ScanUsageRelationship, { label: string; tone: BadgeTone }> = {
    CALLS: { label: "calls", tone: "info" },
    INSTANTIATES: { label: "creates", tone: "warning" },
    READS: { label: "reads", tone: "neutral" },
    WRITES: { label: "writes", tone: "warning" },
    USES: { label: "uses as a type", tone: "neutral" },
    EXTENDS: { label: "extends", tone: "info" },
    IMPLEMENTS: { label: "implements", tone: "info" },
    OVERRIDES: { label: "overrides", tone: "warning" },
};

/**
 * The usages by the file they are in, in the order they came: by file and
 * line.
 */
export function byFile(usages: ScanUsage[]): { file: string | null; usages: ScanUsage[] }[] {
    const files = new Map<string | null, ScanUsage[]>();

    for (const usage of usages) {
        files.set(usage.file, [...(files.get(usage.file) ?? []), usage]);
    }

    return [...files.entries()].map(([file, ofFile]) => ({ file, usages: ofFile }));
}

type Subject = {
    id: string;
    name: string;
};

type UsagesProps = {
    scanId: string;
    subject: Subject;
};

/**
 * Every place, whatever it passes.
 */
const ANY = "";

/**
 * Where the subject is used, a file at a time, with the line and how, and
 * for a call what it passes. Where calls pass arguments by name, as the
 * places that render a component pass its props, the places can be
 * narrowed to those that do not pass one.
 */
function Usages({ scanId, subject }: UsagesProps) {
    const [depth, setDepth] = useState(1);
    const [missing, setMissing] = useState(ANY);

    const { subject: found, usages: all, truncated, loading, error } = useScanUsages({ scanId, nodeId: subject.id, depth });

    // As the scan calls it once it is loaded: for a component, the function it is
    const name = found?.name ?? subject.name;

    const names = argumentNames(all);

    // A name the usages no longer pass, such as after following further, narrows nothing
    const without = names.includes(missing) ? missing : ANY;

    // Those that spread something may pass it, so they stay to be looked at
    const usages = without === ANY
        ? all
        : all.filter((usage) => {
            const passed = passes(usage, without);

            return passed === false || passed === "unknown";
        });

    const files = byFile(usages);

    return (
        <Card>
            <CardHeader
                title={`What a change to ${name} affects`}
                description={
                    loading
                        ? "Looking for what uses it…"
                        : without === ANY
                            ? `${usages.length.toLocaleString()}${truncated ? "+" : ""} place(s) in ${files.length.toLocaleString()} file(s)`
                            : `${usages.length.toLocaleString()} of ${all.length.toLocaleString()}${truncated ? "+" : ""} place(s) do not pass ${without}`
                }
            />
            <CardBody>
                <div className="space-y-4">
                    <div className="flex flex-wrap items-end gap-3">
                        <div className="w-64">
                            <Field label="Follow">
                                <Select
                                    options={DEPTHS}
                                    value={String(depth)}
                                    onChange={(event) => setDepth(Number(event.target.value))}
                                />
                            </Field>
                        </div>

                        {names.length > 0 && (
                            <div className="w-64">
                                <Field label="Places that do not pass">
                                    <Select
                                        options={[
                                            { value: ANY, label: "Show every place" },
                                            ...names.map((argument) => ({ value: argument, label: argument })),
                                        ]}
                                        value={without}
                                        onChange={(event) => setMissing(event.target.value)}
                                    />
                                </Field>
                            </div>
                        )}
                    </div>

                    {error && (
                        <Alert tone="error" title="The usages could not be loaded" announce>
                            {error.message}
                        </Alert>
                    )}

                    {truncated && (
                        <Alert tone="warning">
                            There are more places than fit: the first {usages.length.toLocaleString()} are shown.
                        </Alert>
                    )}

                    {loading && usages.length === 0 && <Spinner size="sm" label="Looking for what uses it" />}

                    {!loading && !error && all.length > 0 && usages.length === 0 && (
                        <EmptyState
                            icon={TargetIcon}
                            title={`Every place passes ${without}`}
                            description="No call of it leaves it out."
                        />
                    )}

                    {!loading && !error && all.length === 0 && (
                        <EmptyState
                            icon={TargetIcon}
                            title="Nothing in the scan uses it"
                            description="No method, function or class of the scan calls, creates, reads, writes, names, extends or overrides it. Scans stored before usages were kept have none: scan the project again to see them."
                        />
                    )}

                    <ul className="space-y-4" aria-label="Usages by file">
                        {files.map(({ file, usages: ofFile }) => (
                            <li key={file ?? ""} className="rounded-card border border-border">
                                <div className="flex items-baseline justify-between gap-3 border-b border-border bg-surface px-4 py-2">
                                    <span className="break-all font-mono text-sm font-medium">{file ?? "Unknown file"}</span>
                                    <Text as="span" size="xs" tone="muted" className="whitespace-nowrap">
                                        {ofFile.length} place(s)
                                    </Text>
                                </div>
                                <ul className="divide-y divide-border">
                                    {ofFile.map((usage, index) => (
                                        <li
                                            key={`${usage.user.id}|${usage.relationship}|${usage.target.id}|${usage.line}|${index}`}
                                            className="flex flex-wrap items-baseline gap-x-3 gap-y-1 px-4 py-2 text-sm"
                                        >
                                            <span className="w-14 shrink-0 font-mono text-xs text-foreground-muted">
                                                {usage.line === null ? "—" : `:${usage.line}`}
                                            </span>
                                            <span className="font-mono">
                                                {usage.owner ? `${usage.owner}.` : ""}{usage.user.name}
                                            </span>
                                            <Badge tone={RELATIONSHIPS[usage.relationship]?.tone ?? "neutral"}>
                                                {RELATIONSHIPS[usage.relationship]?.label ?? usage.relationship}
                                            </Badge>
                                            {/* With its class, which says whose it is when it is
                                                reached through inheritance */}
                                            <Code className="text-xs">
                                                {usage.targetOwner ? `${usage.targetOwner}.` : ""}{usage.target.name}
                                            </Code>
                                            {usage.depth > 1 && (
                                                <Text as="span" size="xs" tone="muted">
                                                    {usage.depth - 1} step(s) away
                                                </Text>
                                            )}
                                            {without !== ANY && passes(usage, without) === "unknown" && (
                                                <Badge tone="neutral">may pass it: spreads</Badge>
                                            )}
                                            {/* What the call passes, when the scan says */}
                                            {usage.arguments && (
                                                <span className="w-full break-all pl-[4.25rem] font-mono text-xs text-foreground-muted">
                                                    {usage.arguments.length === 0
                                                        ? "passes nothing"
                                                        : usage.arguments.map(writtenArgument).join(", ")}
                                                </span>
                                            )}
                                        </li>
                                    ))}
                                </ul>
                            </li>
                        ))}
                    </ul>
                </div>
            </CardBody>
        </Card>
    );
}

export type ImpactAnalysisProps = {
    /**
     * The projects of the system, to choose the one to look in.
     */
    projects: Project[];
    /**
     * What to show the impact of at first, such as for a link from a
     * graph: a node of a scan, and what to call it until its usages are
     * loaded.
     */
    initial?: { scanId: string; nodeId: string; name?: string | null } | null;
};

/**
 * Seeing what a change affects: choose a class, method or function of a
 * project's scan, and see every place that uses it, by file and line.
 */
export function ImpactAnalysis({ projects, initial }: ImpactAnalysisProps) {
    // The scan linked to says which project to look in
    const { scan: linkedScan } = useGetScan({ scanId: initial?.scanId ?? "" });

    const [chosenProjectId, setChosenProjectId] = useState<string | null>(null);
    const projectId = chosenProjectId
        ?? (initial ? linkedScan?.projectId : projects[0]?.id)
        ?? null;

    const { scans } = useListScans(projectId ?? "");

    const [chosenScanId, setChosenScanId] = useState<string | null>(null);

    // The scan linked to until another project or scan is chosen
    const [following, setFollowing] = useState(Boolean(initial));
    const scanId = chosenScanId ?? (following && initial ? initial.scanId : completedScans(scans)[0]?.id) ?? null;

    const [kind, setKind] = useState<Kind>("classes");
    const [query, setQuery] = useState("");
    const [pageNumber, setPageNumber] = useState(0);

    const [subject, setSubject] = useState<Subject | null>(
        initial ? { id: initial.nodeId, name: initial.name || "it" } : null,
    );

    const { page, loading, error } = useScanCode({ scanId, kind, query, page: pageNumber, size: 10, sort: "name" });

    if (projects.length === 0) {
        return (
            <EmptyState
                icon={TargetIcon}
                title="No projects yet"
                description="Impact analysis looks at what a project's scan found. Add a project and scan it first."
            />
        );
    }

    const startOver = () => {
        setSubject(null);
        setPageNumber(0);
        setFollowing(false);
    };

    return (
        <div className="space-y-6">
            <div className="flex flex-wrap items-end gap-3">
                <div className="w-64">
                    <Field label="Project">
                        <Select
                            options={projects.map((project) => ({ value: project.id, label: project.name }))}
                            value={projectId ?? ""}
                            onChange={(event) => {
                                setChosenProjectId(event.target.value);
                                // Another project has scans of its own
                                setChosenScanId(null);
                                startOver();
                            }}
                        />
                    </Field>
                </div>
                <div className="w-72">
                    <Field label="Scan">
                        <ScanSelector
                            scans={scans}
                            value={scanId}
                            onScanChange={(scan) => {
                                setChosenScanId(scan.id);
                                startOver();
                            }}
                            disabled={completedScans(scans).length === 0}
                        />
                    </Field>
                </div>
            </div>

            {!scanId ? (
                <EmptyState
                    icon={TargetIcon}
                    title="No completed scans yet"
                    description="Impact analysis looks at what a scan found. Scan the project, and once the scan completes, choose what to change here."
                />
            ) : (
                <div className="flex flex-col gap-6 xl:flex-row xl:items-start">
                    <div className="w-full shrink-0 space-y-3 xl:w-[28rem]">
                        <div className="flex flex-wrap items-end gap-3">
                            <div className="w-52">
                                <Field label="What would change">
                                    <Select
                                        options={KINDS}
                                        value={kind}
                                        onChange={(event) => {
                                            setKind(event.target.value as Kind);
                                            setPageNumber(0);
                                        }}
                                    />
                                </Field>
                            </div>
                            <div className="min-w-0 flex-1">
                                <SearchInput
                                    label="Search by name"
                                    placeholder="Search by name"
                                    defaultValue={query}
                                    onSearch={(value) => {
                                        setQuery(value);
                                        setPageNumber(0);
                                    }}
                                />
                            </div>
                        </div>

                        {error && (
                            <Alert tone="error" title="The scan's code could not be loaded" announce>
                                {error.message}
                            </Alert>
                        )}

                        <Table<ScanClass | ScanMethod>
                            caption={kind === "classes" ? "Classes" : "Methods and functions"}
                            rows={page.content}
                            rowKey={(item) => item.id}
                            loading={loading}
                            empty={
                                <EmptyState
                                    icon={TargetIcon}
                                    title="Nothing matches"
                                    description="The scan found nothing of this kind with this in its name."
                                />
                            }
                            columns={[
                                {
                                    key: "name",
                                    header: kind === "classes" ? "Class" : "Method",
                                    cell: (item) => (
                                        <div className="min-w-0">
                                            <div className="break-all font-mono text-sm font-medium">
                                                {"parameterNames" in item ? signature(item) : item.name}
                                            </div>
                                            <div className="break-all text-xs text-foreground-muted">
                                                {"parameterNames" in item ? item.className ?? item.file : item.qualifiedName}
                                            </div>
                                        </div>
                                    ),
                                },
                                {
                                    key: "choose",
                                    header: "",
                                    align: "end",
                                    cell: (item) => (
                                        <Button
                                            variant={subject?.id === item.id ? "primary" : "secondary"}
                                            size="sm"
                                            icon={CrosshairIcon}
                                            onClick={() => setSubject({ id: item.id, name: item.name })}
                                        >
                                            Impact
                                        </Button>
                                    ),
                                },
                            ]}
                        />

                        {page.totalPages > 1 && (
                            <Pagination
                                page={page.page}
                                size={10}
                                totalElements={page.totalElements}
                                totalPages={page.totalPages}
                                onPageChange={setPageNumber}
                            />
                        )}
                    </div>

                    <div className="min-w-0 flex-1">
                        {subject ? (
                            // Starts at what uses it itself for another subject
                            <Usages key={subject.id} scanId={scanId} subject={subject} />
                        ) : (
                            <EmptyState
                                icon={TargetIcon}
                                title="Choose what would change"
                                description="Pick a class, method or function at the left to see every place that calls, creates, reads, writes or extends it: the files a change to it lands in."
                            />
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
