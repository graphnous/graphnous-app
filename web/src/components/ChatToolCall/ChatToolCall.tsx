"use client";

import { Alert, Spinner, Text } from "@graphnous/theme";
import {
    CaretRightIcon,
    CubeIcon,
    FolderIcon,
    GraphIcon,
    MagnifyingGlassIcon,
    PackageIcon,
    ScanIcon,
    TagIcon,
    WrenchIcon,
    type Icon as PhosphorIcon,
} from "@phosphor-icons/react";
import type { ReactNode } from "react";

import {
    AnnotatedView,
    ClassView,
    ClassesView,
    DependenciesView,
    ProjectsView,
    ScanGraphView,
    ScansView,
    SystemsView,
} from "./toolViews";
import {
    parseResult,
    type AnnotatedElement,
    type ClassDetails,
    type ClassSummary,
    type Dependency,
    type ProjectSummary,
    type ScanGraphSummary,
    type ScanSummary,
    type SystemSummary,
} from "./toolResults";

export type ChatToolCallProps = {
    name: string;
    parameters: unknown;
    status: "inProgress" | "executing" | "complete";
    /**
     * The tool's result, once it is complete: JSON, or the message it
     * failed with.
     */
    result?: string;
};

type Parameters = Record<string, string | undefined>;

/**
 * How a tool call shows in the chat: what it looks up, and then what it
 * found.
 */
type ToolView = {
    icon: PhosphorIcon;
    /**
     * What the call does, such as "Finding classes matching Order".
     */
    label: (parameters: Parameters) => string;
    /**
     * What the call found, from its result.
     */
    render: (result: unknown, parameters: Parameters) => ReactNode;
    /**
     * How much it found, shown next to the label.
     */
    count?: (result: unknown) => number;
};

const length = (result: unknown) => (Array.isArray(result) ? result.length : 0);

const VIEWS: Record<string, ToolView> = {
    listSystems: {
        icon: GraphIcon,
        label: () => "Systems",
        count: length,
        render: (result) => <SystemsView systems={result as SystemSummary[]} />,
    },
    listProjects: {
        icon: FolderIcon,
        label: () => "Projects of the system",
        count: length,
        render: (result) => <ProjectsView projects={result as ProjectSummary[]} />,
    },
    listScans: {
        icon: ScanIcon,
        label: () => "Scans of the project",
        count: length,
        render: (result, parameters) => (
            <ScansView scans={result as ScanSummary[]} projectId={parameters.projectId} />
        ),
    },
    getScanGraph: {
        icon: GraphIcon,
        label: () => "Outline of the scan's graph",
        render: (result) => <ScanGraphView graph={result as ScanGraphSummary} />,
    },
    findClasses: {
        icon: MagnifyingGlassIcon,
        label: ({ query }) => (query ? `Classes matching “${query}”` : "Classes"),
        count: length,
        render: (result) => <ClassesView classes={result as ClassSummary[]} />,
    },
    getClass: {
        icon: CubeIcon,
        label: ({ qualifiedName }) => qualifiedName ?? "Class",
        render: (result) => <ClassView details={result as ClassDetails} />,
    },
    findAnnotated: {
        icon: TagIcon,
        label: ({ annotation }) => `Annotated with @${(annotation ?? "").replace(/^@/, "")}`,
        count: length,
        render: (result) => <AnnotatedView elements={result as AnnotatedElement[]} />,
    },
    listDependencies: {
        icon: PackageIcon,
        label: () => "Dependencies",
        count: length,
        render: (result) => <DependenciesView dependencies={result as Dependency[]} />,
    },
};

/**
 * A tool call of the assistant in the chat: a line saying what it looks
 * up while it runs, then what it found, folded open below it. Tools
 * without a view of their own show their name and raw result.
 */
export function ChatToolCall({ name, parameters, status, result }: ChatToolCallProps) {
    const view = VIEWS[name];
    const args = (parameters ?? {}) as Parameters;

    const Icon = view?.icon ?? WrenchIcon;
    const label = view ? view.label(args) : name;

    if (status !== "complete") {
        return (
            <div className="my-2 flex items-center gap-2 text-sm text-foreground-muted" aria-busy="true">
                <Spinner size="sm" label={`Looking up ${label}`} />
                <span>{label}…</span>
            </div>
        );
    }

    const parsed = parseResult<unknown>(result);

    if (!parsed.ok) {
        return (
            <Alert tone="error" title={`Could not look up ${label}`} className="my-2">
                {parsed.message}
            </Alert>
        );
    }

    const count = view?.count?.(parsed.value);

    return (
        <details className="group my-2 rounded-card border border-border bg-surface text-sm" open={count !== 0}>
            <summary className="flex cursor-pointer list-none items-center gap-2 px-3 py-2 [&::-webkit-details-marker]:hidden">
                <CaretRightIcon className="size-3 shrink-0 transition-transform group-open:rotate-90" aria-hidden />
                <Icon className="size-4 shrink-0 text-foreground-muted" aria-hidden />
                <span className="min-w-0 flex-1 truncate font-medium">{label}</span>
                {count !== undefined && (
                    <Text as="span" size="xs" tone="muted">
                        {count === 1 ? "1 result" : `${count} results`}
                    </Text>
                )}
            </summary>

            <div className="max-h-96 overflow-auto border-t border-border px-3 py-2">
                {view ? (
                    view.render(parsed.value, args)
                ) : (
                    <pre className="whitespace-pre-wrap break-all font-mono text-xs">
                        {JSON.stringify(parsed.value, null, 2)}
                    </pre>
                )}
            </div>
        </details>
    );
}
