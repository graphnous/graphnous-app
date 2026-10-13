"use client";

import { Badge, Button, EmptyState, Link, Spinner, Text, Timestamp } from "@graphnous/theme";
import { ClockCounterClockwiseIcon } from "@phosphor-icons/react";
import { useState } from "react";

import { NODE_TYPES } from "@/components/ScanGraphCanvas/graphStyle";
import { ScanRevision } from "@/components/ScanRevision/ScanRevision";
import { completedScans } from "@/components/ScanSelector/ScanSelector";
import { useCompareScans } from "@/lib/hooks/scan/comparison/useCompareScans";
import type { Scan, ScanComparisonSummary } from "@/types";

/**
 * How many scans show at first, and how many more each time: each one is
 * compared with the one before it.
 */
const STEP = 10;

/**
 * What a type of compared node is called when there are several, such as
 * "classes".
 */
function plural(type: ScanComparisonSummary["type"]): string {
    const label = (NODE_TYPES[type]?.label ?? type).toLowerCase();

    return label.endsWith("s") ? `${label}es` : label.endsWith("y") ? `${label.slice(0, -1)}ies` : `${label}s`;
}

/**
 * The totals of a comparison's summary: how many nodes were added, removed
 * and changed, of every type.
 */
export function totals(summary: ScanComparisonSummary[]): { added: number; removed: number; changed: number } {
    return summary.reduce(
        (total, type) => ({
            added: total.added + type.added,
            removed: total.removed + type.removed,
            changed: total.changed + type.changed,
        }),
        { added: 0, removed: 0, changed: 0 },
    );
}

type ScanChangesProps = {
    scan: Scan;
    /**
     * The completed scan before it; none for the project's first.
     */
    previous: Scan | null;
};

/**
 * What a scan changed since the one before it: how much in all, then by
 * type.
 */
function ScanChanges({ scan, previous }: ScanChangesProps) {
    const { comparison, error } = useCompareScans(previous?.id ?? null, scan.id);

    if (!previous) {
        return <Text size="sm" tone="muted">The first completed scan of the project.</Text>;
    }

    if (error) {
        return <Text size="sm" tone="muted">{error.message}</Text>;
    }

    if (!comparison) {
        return <Spinner size="sm" label="Comparing with the scan before it" />;
    }

    if (comparison.summary.length === 0) {
        return <Text size="sm" tone="muted">No differences with the scan before it.</Text>;
    }

    const { added, removed, changed } = totals(comparison.summary);

    return (
        <div className="space-y-2">
            <div className="flex flex-wrap gap-2">
                {added > 0 && <Badge tone="success">{added.toLocaleString()} added</Badge>}
                {removed > 0 && <Badge tone="error">{removed.toLocaleString()} removed</Badge>}
                {changed > 0 && <Badge tone="warning">{changed.toLocaleString()} changed</Badge>}
            </div>
            <ul className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-foreground-secondary">
                {comparison.summary.map((type) => (
                    <li key={type.type} className="flex items-center gap-1.5 whitespace-nowrap">
                        <span
                            className="size-2.5 shrink-0 rounded-full"
                            style={{ backgroundColor: `var(${NODE_TYPES[type.type]?.token ?? "--color-foreground-muted"})` }}
                            aria-hidden
                        />
                        {plural(type.type)}
                        <span className="font-mono text-xs">
                            {[
                                type.added > 0 && `+${type.added}`,
                                type.removed > 0 && `−${type.removed}`,
                                type.changed > 0 && `~${type.changed}`,
                            ].filter(Boolean).join(" ")}
                        </span>
                    </li>
                ))}
            </ul>
        </div>
    );
}

export type ScanTimelineProps = {
    /**
     * The project's scans; the completed ones are listed.
     */
    scans: Scan[];
};

/**
 * The completed scans of a project, the newest first, each with what it
 * changed since the one before it: what was added, removed and changed, in
 * all and by type.
 */
export function ScanTimeline({ scans }: ScanTimelineProps) {
    const completed = completedScans(scans);
    const [shown, setShown] = useState(STEP);

    if (completed.length === 0) {
        return (
            <EmptyState
                icon={ClockCounterClockwiseIcon}
                title="No completed scans yet"
                description="The history is what each scan changed since the one before it. Scan the project, and once the scan completes, it shows here."
            />
        );
    }

    return (
        <div className="space-y-4 pt-4">
            <ol className="relative space-y-6 border-l border-border pl-6" aria-label="History">
                {completed.slice(0, shown).map((scan, index) => (
                    <li key={scan.id} className="relative">
                        <span
                            className="absolute -left-[1.85rem] top-1.5 size-2.5 rounded-full border-2 border-surface bg-foreground-muted"
                            aria-hidden
                        />
                        <div className="mb-2 flex flex-wrap items-center gap-x-3 gap-y-1">
                            <Link href={`/scans/${scan.id}`} className="font-medium">{scan.branch}</Link>
                            <ScanRevision revision={scan.revision} requestedRevision={scan.requestedRevision} />
                            <Text as="span" size="sm" tone="muted">
                                <Timestamp value={scan.completedAt ?? scan.createdAt} />
                            </Text>
                        </div>
                        <ScanChanges scan={scan} previous={completed[index + 1] ?? null} />
                    </li>
                ))}
            </ol>

            {completed.length > shown && (
                <Button variant="ghost" size="sm" onClick={() => setShown(shown + STEP)}>
                    Show {Math.min(STEP, completed.length - shown)} more of {completed.length - shown}
                </Button>
            )}
        </div>
    );
}
