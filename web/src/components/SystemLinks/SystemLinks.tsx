"use client";

import { Badge, Checkbox, Code, EmptyState, Link, Table, Text } from "@graphnous/theme";
import { ArrowsLeftRightIcon } from "@phosphor-icons/react";
import { useState } from "react";

import type { Project, SystemLink } from "@/types";

/**
 * How many links show at most: a system can have very many.
 */
const SHOWN = 200;

export type SystemLinksProps = {
    links: SystemLink[];
    /**
     * How many HTTP calls and endpoints there were to link.
     */
    calls: number;
    endpoints: number;
    /**
     * The projects of the system, to call each end's project by name.
     */
    projects: Project[];
    loading?: boolean;
    /**
     * Called with the id of a call or endpoint and its scan, such as to
     * focus a graph on it.
     */
    onFocus?: (scanId: string, nodeId: string) => void;
};

/**
 * The HTTP calls that request an endpoint, each with the project that
 * calls and the project that serves: how projects use each other. Only
 * those between different projects at first.
 */
export function SystemLinks({ links, calls, endpoints, projects, loading, onFocus }: SystemLinksProps) {
    const [withinProjects, setWithinProjects] = useState(false);

    const shown = withinProjects ? links : links.filter((link) => link.crossesProjects);
    const linkedCalls = new Set(links.map((link) => link.call.id)).size;

    const project = (id: string) => (
        <Link href={`/projects/${id}`}>{projects.find((candidate) => candidate.id === id)?.name ?? id}</Link>
    );

    return (
        <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
                <Text size="sm" tone="secondary">
                    {linkedCalls.toLocaleString()} of {calls.toLocaleString()} HTTP calls request one of{" "}
                    {endpoints.toLocaleString()} endpoints.
                </Text>
                <Checkbox
                    label="Also within a project"
                    checked={withinProjects}
                    onChange={() => setWithinProjects(!withinProjects)}
                />
            </div>

            <Table<SystemLink>
                caption="HTTP links"
                rows={shown.slice(0, SHOWN)}
                rowKey={(link) => `${link.call.id}->${link.endpoint.id}`}
                loading={loading}
                empty={
                    <EmptyState
                        icon={ArrowsLeftRightIcon}
                        title={calls === 0 || endpoints === 0 ? "Nothing to link yet" : "No links found"}
                        description={
                            calls === 0 || endpoints === 0
                                ? "Links are between the HTTP calls and the endpoints enhancers find. The scans shown have "
                                    + `${calls} HTTP call(s) and ${endpoints} endpoint(s); run the enhancers for both sides on them.`
                                : withinProjects
                                    ? "No HTTP call of the scans shown has the path of one of their endpoints."
                                    : "No HTTP call of one project has the path of an endpoint of another. Calls within a project are left out."
                        }
                    />
                }
                columns={[
                    { key: "from", header: "Calls from", cell: (link) => project(link.call.projectId) },
                    {
                        key: "call",
                        header: "Call",
                        cell: (link) => (
                            <button
                                type="button"
                                className="break-all text-left font-mono text-sm hover:underline"
                                onClick={() => onFocus?.(link.call.scanId, link.call.id)}
                            >
                                {[link.call.httpMethod, link.call.url].filter(Boolean).join(" ")}
                            </button>
                        ),
                    },
                    { key: "to", header: "Served by", cell: (link) => project(link.endpoint.projectId) },
                    {
                        key: "endpoint",
                        header: "Endpoint",
                        cell: (link) => (
                            <button
                                type="button"
                                className="flex items-baseline gap-2 text-left hover:underline"
                                onClick={() => onFocus?.(link.endpoint.scanId, link.endpoint.id)}
                            >
                                <Badge>{link.endpoint.httpMethod}</Badge>
                                <Code className="break-all text-xs">{link.endpoint.url}</Code>
                            </button>
                        ),
                    },
                    {
                        key: "match",
                        header: "Match",
                        cell: (link) => (
                            link.match === "PATH"
                                ? <Text as="span" size="sm" tone="muted">Whole path</Text>
                                : <Badge tone="warning">End of path</Badge>
                        ),
                    },
                ]}
            />

            {shown.length > SHOWN && (
                <Text size="sm" tone="muted">
                    The first {SHOWN} of {shown.length.toLocaleString()} links are shown.
                </Text>
            )}
        </div>
    );
}
