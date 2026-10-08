"use client";

import { Badge, Code, DescriptionList, Link, Text, type BadgeTone } from "@graphnous/theme";
import type { ReactNode } from "react";

import { ScanRevision } from "@/components/ScanRevision/ScanRevision";
import { ScanStatusBadge } from "@/components/ScanStatusBadge/ScanStatusBadge";

import type {
    Annotation,
    AnnotatedElement,
    ClassDetails,
    ClassSummary,
    Dependency,
    Method,
    ProjectSummary,
    ScanGraphSummary,
    ScanSummary,
    SystemSummary,
} from "./toolResults";

/**
 * The views of what the assistant's tools found, small enough for the chat.
 */

function Empty({ children }: { children: ReactNode }) {
    return <Text size="sm" tone="muted">{children}</Text>;
}

/**
 * A compact table; the theme's Table is made for pages, with sorting and
 * actions the chat has no use for.
 */
function MiniTable<Row>({
    caption,
    columns,
    rows,
    rowKey,
}: {
    caption: string;
    columns: { header: string; cell: (row: Row) => ReactNode; align?: "end" }[];
    rows: Row[];
    rowKey: (row: Row, index: number) => string;
}) {
    return (
        <table className="w-full text-left text-xs">
            <caption className="sr-only">{caption}</caption>
            <thead>
                <tr className="text-foreground-muted">
                    {columns.map((column) => (
                        <th
                            key={column.header}
                            scope="col"
                            className={`py-1 pr-3 font-medium ${column.align === "end" ? "text-right" : ""}`}
                        >
                            {column.header}
                        </th>
                    ))}
                </tr>
            </thead>
            <tbody>
                {rows.map((row, index) => (
                    <tr key={rowKey(row, index)} className="border-t border-border align-top">
                        {columns.map((column) => (
                            <td
                                key={column.header}
                                className={`py-1 pr-3 ${column.align === "end" ? "text-right tabular-nums" : ""}`}
                            >
                                {column.cell(row)}
                            </td>
                        ))}
                    </tr>
                ))}
            </tbody>
        </table>
    );
}

const KIND_TONES: Record<string, BadgeTone> = {
    CLASS: "info",
    INTERFACE: "success",
    ENUM: "warning",
    RECORD: "neutral",
    ANNOTATION: "neutral",
    METHOD: "info",
    FIELD: "neutral",
    FUNCTION: "info",
    VARIABLE: "neutral",
};

function KindBadge({ kind }: { kind?: string | null }) {
    if (!kind) {
        return null;
    }

    return <Badge tone={KIND_TONES[kind] ?? "neutral"}>{kind.toLowerCase()}</Badge>;
}

/**
 * The annotation as written in code, with its arguments when it has any.
 */
function AnnotationText({ annotation }: { annotation: Annotation }) {
    return (
        <Code className="text-xs">
            @{annotation.name}
            {annotation.arguments ? `(${formatArguments(annotation.arguments)})` : ""}
        </Code>
    );
}

/**
 * The arguments as in code: value="/orders" becomes "/orders", the others
 * name = value.
 */
function formatArguments(json: string): string {
    try {
        const args = JSON.parse(json) as Record<string, unknown>;
        const entries = Object.entries(args);

        if (entries.length === 1 && entries[0][0] === "value") {
            return JSON.stringify(entries[0][1]);
        }

        return entries.map(([name, value]) => `${name} = ${JSON.stringify(value)}`).join(", ");
    } catch {
        return json;
    }
}

/**
 * The simple name of a type, keeping its type arguments simple too:
 * java.util.List<com.example.Order> becomes List<Order>.
 */
function simpleType(type?: string | null): string {
    return (type ?? "").replace(/(?:[a-z_$][\w$]*\.)+([A-Z_$][\w$]*)/g, "$1");
}

export function SystemsView({ systems }: { systems: SystemSummary[] }) {
    if (systems.length === 0) {
        return <Empty>No systems yet.</Empty>;
    }

    return (
        <ul className="space-y-1">
            {systems.map((system) => (
                <li key={system.id}>
                    <span className="font-medium">{system.name}</span>
                    {system.description && <Text as="span" size="sm" tone="secondary"> — {system.description}</Text>}
                </li>
            ))}
        </ul>
    );
}

export function ProjectsView({ projects }: { projects: ProjectSummary[] }) {
    if (projects.length === 0) {
        return <Empty>The system has no projects.</Empty>;
    }

    return (
        <MiniTable
            caption="Projects"
            rows={projects}
            rowKey={(project) => project.id}
            columns={[
                {
                    header: "Project",
                    cell: (project) => <Link href={`/projects/${project.id}`}>{project.name}</Link>,
                },
                {
                    header: "Repository",
                    cell: (project) => (
                        <span className="break-all text-foreground-secondary">
                            {project.gitUrl}
                            {project.path ? ` · ${project.path}` : ""}
                        </span>
                    ),
                },
            ]}
        />
    );
}

export function ScansView({ scans, projectId }: { scans: ScanSummary[]; projectId?: string }) {
    if (scans.length === 0) {
        return <Empty>The project has not been scanned.</Empty>;
    }

    return (
        <MiniTable
            caption="Scans"
            rows={scans}
            rowKey={(scan) => scan.id}
            columns={[
                { header: "Status", cell: (scan) => <ScanStatusBadge status={scan.status} /> },
                {
                    header: "Branch",
                    cell: (scan) =>
                        projectId ? (
                            <Link href={`/projects/${projectId}/scans/${scan.id}`}>{scan.branch ?? "—"}</Link>
                        ) : (
                            (scan.branch ?? "—")
                        ),
                },
                {
                    header: "Revision",
                    cell: (scan) => <ScanRevision revision={scan.revision} requestedRevision={scan.requestedRevision} />,
                },
                { header: "Created", cell: (scan) => formatInstant(scan.createdAt) },
            ]}
        />
    );
}

/**
 * An instant as the server sends it: ISO 8601, or seconds since the epoch.
 */
function formatInstant(value?: string | number | null): string {
    if (value === undefined || value === null) {
        return "—";
    }

    const date = typeof value === "number" ? new Date(value * 1000) : new Date(value);

    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

export function ScanGraphView({ graph }: { graph: ScanGraphSummary }) {
    if (graph.targets.length === 0) {
        return (
            <Empty>
                {graph.status === "COMPLETED"
                    ? "The scan found nothing to put in the graph."
                    : `The scan is ${graph.status.toLowerCase()}, so it has no graph yet.`}
            </Empty>
        );
    }

    return (
        <div className="space-y-3">
            {graph.targets.map((target) => (
                <section key={target.path} className="space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                        <Code className="text-xs">{target.path}</Code>
                        {target.language && (
                            <Badge tone="info">
                                {target.language.toLowerCase()} {target.languageVersion ?? ""}
                            </Badge>
                        )}
                        {target.buildSystem && <Badge>{target.buildSystem.toLowerCase()}</Badge>}
                    </div>

                    <MiniTable
                        caption={`Modules of ${target.path}`}
                        rows={target.modules}
                        rowKey={(module) => module.path}
                        columns={[
                            {
                                header: "Module",
                                cell: (module) => (
                                    <span>
                                        <span className="font-medium">{module.name}</span>
                                        {module.path !== module.name && (
                                            <Text as="span" size="xs" tone="muted"> {module.path}</Text>
                                        )}
                                    </span>
                                ),
                            },
                            { header: "Files", align: "end", cell: (module) => module.files },
                            { header: "Packages", align: "end", cell: (module) => module.packages },
                            { header: "Classes", align: "end", cell: (module) => module.classes },
                            { header: "Methods", align: "end", cell: (module) => module.methods },
                            { header: "Dependencies", align: "end", cell: (module) => module.dependencies },
                        ]}
                    />
                </section>
            ))}
        </div>
    );
}

export function ClassesView({ classes }: { classes: ClassSummary[] }) {
    if (classes.length === 0) {
        return <Empty>No classes match.</Empty>;
    }

    return (
        <MiniTable
            caption="Classes"
            rows={classes}
            rowKey={(type, index) => `${type.module}|${type.qualifiedName}|${index}`}
            columns={[
                { header: "Kind", cell: (type) => <KindBadge kind={type.kind} /> },
                {
                    header: "Class",
                    cell: (type) => (
                        <span title={type.qualifiedName}>
                            <span className="font-medium">{type.name}</span>
                            <Text as="span" size="xs" tone="muted">
                                {" "}
                                {type.qualifiedName.slice(0, -type.name.length - 1)}
                            </Text>
                        </span>
                    ),
                },
                { header: "Module", cell: (type) => type.module ?? "—" },
            ]}
        />
    );
}

function signature(method: Method): string {
    const parameters = method.parameterTypes
        .map((type, index) => `${simpleType(type)} ${method.parameterNames[index] ?? ""}`.trim())
        .join(", ");

    return method.kind === "CONSTRUCTOR"
        ? `${method.name}(${parameters})`
        : `${simpleType(method.returnType) || "void"} ${method.name}(${parameters})`;
}

export function ClassView({ details }: { details: ClassDetails }) {
    const supertypes = [details.superClass, ...details.interfaces].filter(Boolean) as string[];

    return (
        <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-2">
                <KindBadge kind={details.kind} />
                {details.modifiers.map((modifier) => (
                    <Badge key={modifier}>{modifier.toLowerCase()}</Badge>
                ))}
                {details.annotations.map((annotation, index) => (
                    <AnnotationText key={index} annotation={annotation} />
                ))}
            </div>

            <DescriptionList
                layout="inline"
                items={[
                    { label: "File", value: details.file && <Code className="text-xs">{details.file}</Code> },
                    { label: "Module", value: details.module },
                    { label: "Package", value: details.packageName },
                    {
                        label: "Extends / implements",
                        value: supertypes.length > 0 && (
                            <span className="font-mono text-xs">{supertypes.map(simpleType).join(", ")}</span>
                        ),
                    },
                    {
                        label: "Subtypes",
                        value: details.subtypes.length > 0 && (
                            <span className="font-mono text-xs">{details.subtypes.map(simpleType).join(", ")}</span>
                        ),
                    },
                ]}
            />

            {details.fields.length > 0 && (
                <section>
                    <Text size="xs" weight="semibold" tone="secondary">Fields</Text>
                    <ul className="mt-1 space-y-0.5 font-mono text-xs">
                        {details.fields.map((field) => (
                            <li key={field.name}>
                                {field.annotations.map((annotation, index) => (
                                    <span key={index} className="mr-1 text-foreground-muted">@{annotation.name}</span>
                                ))}
                                <span className="text-foreground-muted">{simpleType(field.type)}</span> {field.name}
                            </li>
                        ))}
                    </ul>
                </section>
            )}

            {details.methods.length > 0 && (
                <section>
                    <Text size="xs" weight="semibold" tone="secondary">Methods</Text>
                    <ul className="mt-1 space-y-0.5 font-mono text-xs">
                        {details.methods.map((method, index) => (
                            <li key={index}>
                                {method.annotations
                                    .filter((annotation) => !annotation.parameter)
                                    .map((annotation, annotationIndex) => (
                                        <span key={annotationIndex} className="mr-1 text-foreground-muted">
                                            @{annotation.name}
                                        </span>
                                    ))}
                                {signature(method)}
                            </li>
                        ))}
                    </ul>
                </section>
            )}
        </div>
    );
}

export function AnnotatedView({ elements }: { elements: AnnotatedElement[] }) {
    if (elements.length === 0) {
        return <Empty>Nothing has this annotation.</Empty>;
    }

    return (
        <MiniTable
            caption="Annotated classes, methods and fields"
            rows={elements}
            rowKey={(element, index) => `${element.className}|${element.member}|${index}`}
            columns={[
                { header: "Kind", cell: (element) => <KindBadge kind={element.kind} /> },
                {
                    header: "Where",
                    cell: (element) => (
                        <span className="font-mono" title={element.className ?? element.member ?? undefined}>
                            {element.className ? simpleType(element.className) : ""}
                            {element.className && element.member ? "." : ""}
                            {element.member ?? ""}
                            {element.annotation.parameter ? ` (${element.annotation.parameter})` : ""}
                        </span>
                    ),
                },
                { header: "Annotation", cell: (element) => <AnnotationText annotation={element.annotation} /> },
            ]}
        />
    );
}

export function DependenciesView({ dependencies }: { dependencies: Dependency[] }) {
    if (dependencies.length === 0) {
        return <Empty>No dependencies.</Empty>;
    }

    const modules = new Set(dependencies.map((dependency) => dependency.module));

    return (
        <MiniTable
            caption="Dependencies"
            rows={dependencies}
            rowKey={(dependency, index) => `${dependency.module}|${dependency.name}|${index}`}
            columns={[
                ...(modules.size > 1
                    ? [{ header: "Module", cell: (dependency: Dependency) => dependency.module }]
                    : []),
                { header: "Library", cell: (dependency) => <span className="font-mono">{dependency.name}</span> },
                { header: "Version", cell: (dependency) => dependency.version ?? "—" },
                {
                    header: "Scope",
                    cell: (dependency) =>
                        dependency.scope ? (
                            <Badge tone={dependency.scope === "test" ? "neutral" : "info"}>{dependency.scope}</Badge>
                        ) : (
                            "—"
                        ),
                },
            ]}
        />
    );
}
