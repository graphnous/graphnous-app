"use client";

import {
    Column,
    ConfirmDialog,
    EmptyState,
    IconButton,
    Menu,
    Sort,
    Table,
    useToast,
} from "@graphnous/theme";
import { DotsThreeIcon, TrashIcon } from "@phosphor-icons/react";

import { ScanRevision } from "@/components/ScanRevision/ScanRevision";
import { useDeleteScan } from "@/lib/hooks/scan/useDeleteScan";
import type { Page, Scan, ScanStatus } from "@/types";
import { useState } from "react";

export type ScansTableProps = {
    page: Page<Scan>;
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
    onScanDeleted,
}: ScansTableProps) {
    const [sort, setSort] = useState<Sort>({
        field: 'createdAt',
        direction: 'asc'
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

    const columns: Column<Scan>[] = [
        {
            key: 'branch',
            header: 'Branch',
            sortable: true,
            cell: (scan) => <span className="font-medium">
                {scan.branch}
            </span>
        },
        {
            key: 'revision',
            header: 'Revision',
            cell: (scan) => <ScanRevision revision={scan.revision} requestedRevision={scan.requestedRevision} />
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

    const rows = page.content.sort((a, b) => {
        const field = sort.field as 'branch' | 'revision';
        // A scan without a revision yet sorts first
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
