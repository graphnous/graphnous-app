"use client";

import {
    Column,
    ConfirmDialog,
    EmptyState,
    IconButton,
    Link,
    Menu,
    Sort,
    Table,
    Timestamp,
    useToast,
} from "@graphnous/theme";
import { DotsThreeIcon, TrashIcon } from "@phosphor-icons/react";

import { ScanRevision } from "@/components/ScanRevision/ScanRevision";
import { ScanStatusBadge } from "@/components/ScanStatusBadge/ScanStatusBadge";
import { ScanTriggerBadge } from "@/components/ScanTriggerBadge/ScanTriggerBadge";
import { useDeleteScan } from "@/lib/hooks/scan/useDeleteScan";
import type { Page, Project, Scan, ScanStatus } from "@/types";
import { useState } from "react";

export type ScansTableProps = {
    page: Page<Scan>;
    /**
     * The projects the scans may be of, to show each scan's project in a
     * first column, such as for the scans of a whole system; no column
     * without them.
     */
    projects?: Project[];
    /**
     * Called after a scan was deleted, such as to load the scans again.
     */
    onScanDeleted?: (scan: Scan) => void;
};

/**
 * Statuses of a scan that is still writing its results, which the API
 * refuses to delete.
 */
const ACTIVE: ScanStatus[] = ["PENDING", "QUEUED", "RUNNING"];

/**
 * The scans of a project, with the actions on each in its last column.
 */
export function ScansTable({
    page,
    projects,
    onScanDeleted,
}: ScansTableProps) {
    const [sort, setSort] = useState<Sort>({
        field: 'createdAt',
        // The newest first
        direction: 'desc'
    });

    // The scan whose delete is being confirmed
    const [deleting, setDeleting] = useState<Scan | null>(null);

    const { deleteScan, loading, error, reset } = useDeleteScan();
    const { toast } = useToast();

    if (!page || !page.content) {
        return <EmptyState title={"Scans"} />
    }

    const closeDelete = () => {
        setDeleting(null);
        reset();
    };

    const confirmDelete = async () => {
        if (!deleting) {
            return;
        }

        try {
            await deleteScan(deleting.id);
        } catch {
            // Shown in the dialog, through the hook's error
            return;
        }

        toast({ title: "Scan deleted", tone: "success" });
        onScanDeleted?.(deleting);
        closeDelete();
    };

    const projectsById = new Map(projects?.map((project) => [project.id, project]));

    const projectColumn: Column<Scan>[] = projects ? [
        {
            key: 'project',
            header: 'Project',
            cell: (scan) => {
                const project = projectsById.get(scan.projectId);

                return (
                    <Link href={`/projects/${scan.projectId}`}>
                        {project?.name ?? 'Unknown project'}
                    </Link>
                );
            }
        }
    ] : [];

    const columns: Column<Scan>[] = [
        ...projectColumn,
        {
            key: 'branch',
            header: 'Branch',
            sortable: true,
            cell: (scan) => <Link href={`/scans/${scan.id}`} className="font-medium">
                {scan.branch}
            </Link>
        },
        {
            key: 'revision',
            header: 'Revision',
            cell: (scan) => <ScanRevision revision={scan.revision} requestedRevision={scan.requestedRevision} />
        },
        {
            key: 'status',
            header: 'Status',
            cell: (scan) => <ScanStatusBadge status={scan.status} />
        },
        {
            key: 'trigger',
            header: 'Trigger',
            cell: (scan) => <ScanTriggerBadge trigger={scan.trigger} />
        },
        {
            key: 'startedAt',
            header: 'Started',
            sortable: true,
            cell: (scan) => <Moment value={scan.startedAt} />
        },
        {
            key: 'completedAt',
            header: 'Completed',
            sortable: true,
            cell: (scan) => <Moment value={scan.completedAt} />
        },
        {
            key: 'action',
            header: 'Action',
            cell: (scan) => (
                <Menu
                    trigger={
                        <IconButton
                            icon={DotsThreeIcon}
                            label={`Actions for scan of ${scan.branch || scan.revision || scan.id}`}
                            variant="ghost"
                            size="sm"
                        />
                    }
                    items={[
                        {
                            label: "Delete scan",
                            icon: TrashIcon,
                            danger: true,
                            disabled: ACTIVE.includes(scan.status),
                            onSelect: () => setDeleting(scan),
                        },
                    ]}
                />
            )
        }
    ]

    // A copy, as the scans are the caller's
    const rows = [...page.content].sort((a, b) => {
        const field = sort.field as 'branch' | 'revision' | 'startedAt' | 'completedAt';
        // A scan without a revision or time yet sorts first; the times are
        // ISO 8601, which sort as text
        const order = (a[field] ?? '').localeCompare(b[field] ?? '');
        return sort.direction === 'asc' ? order : -order;
    });

    return (
        <>
            <Table
                rows={rows}
                sort={sort}
                columns={columns}
                caption={"Scans"}
                rowKey={(p) => p.id}
                onSortChange={(next) => {
                    setSort(next);
                }}>

            </Table>

            <ConfirmDialog
                open={deleting !== null}
                onClose={closeDelete}
                onConfirm={confirmDelete}
                title="Delete scan?"
                description="The scan is deleted with its results in the graph, its logs and its steps. This cannot be undone."
                confirmLabel="Delete scan"
                danger
                confirming={loading}
                error={error?.message}
            />
        </>
    );
}

/**
 * When something happened, or a dash when it has not yet.
 */
function Moment({ value }: { value: string | null | undefined }) {
    return value ? <Timestamp value={value} /> : <span aria-label="Not yet">–</span>;
}
