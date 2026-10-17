"use client";

import { Alert, Button, Field, Link, Text, TextInput } from "@graphnous/theme";
import { CheckCircleIcon, ScanIcon, XCircleIcon } from "@phosphor-icons/react";
import { useState } from "react";

/**
 * Where an approval stands: waiting for the user, starting the scan, or
 * done one way or the other.
 */
export type ScanApprovalState =
    | { status: "pending" }
    | { status: "starting" }
    | { status: "started"; scanId: string; branch: string; revision?: string }
    | { status: "declined" }
    | { status: "failed"; message: string };

export type ScanApprovalProps = {
    projectId: string;
    /**
     * The project's name, as the API has it; its id until it is loaded.
     */
    projectName?: string;
    /**
     * What the assistant asked to scan, which the user can change first.
     */
    branch?: string;
    revision?: string;
    /**
     * Why the assistant wants the scan, in its words.
     */
    reason?: string;
    state: ScanApprovalState;
    onApprove: (request: { branch: string; revision?: string }) => void;
    onDecline: () => void;
};

/**
 * The assistant asking to start a scan: nothing is scanned until the user
 * starts it here, with the branch and revision they choose.
 */
export function ScanApproval({
    projectId,
    projectName,
    branch: proposedBranch,
    revision: proposedRevision,
    reason,
    state,
    onApprove,
    onDecline,
}: ScanApprovalProps) {
    const [branch, setBranch] = useState(proposedBranch ?? "");
    const [revision, setRevision] = useState(proposedRevision ?? "");

    const name = projectName ?? projectId;

    if (state.status === "started") {
        return (
            <div className="my-2 flex items-center gap-2 text-sm">
                <CheckCircleIcon className="size-4 text-success" aria-hidden />
                <span>
                    Started a scan of {name} at {state.revision ? `${state.branch} (${state.revision})` : state.branch}.{" "}
                    <Link href={`/projects/${encodeURIComponent(projectId)}/scans/${encodeURIComponent(state.scanId)}`}>
                        Follow the scan
                    </Link>
                </span>
            </div>
        );
    }

    if (state.status === "declined") {
        return (
            <div className="my-2 flex items-center gap-2 text-sm text-foreground-muted">
                <XCircleIcon className="size-4" aria-hidden />
                <span>You declined to scan {name}.</span>
            </div>
        );
    }

    const starting = state.status === "starting";

    return (
        <section
            aria-label={`Start a scan of ${name}?`}
            className="my-2 flex flex-col gap-3 rounded-md border border-border bg-surface p-4"
        >
            <div className="flex items-center gap-2">
                <ScanIcon className="size-5 text-foreground-muted" aria-hidden />
                <Text weight="semibold">Start a scan of {name}?</Text>
            </div>

            {reason && (
                <Text size="sm" tone="muted">
                    {reason}
                </Text>
            )}

            <Field label="Branch">
                <TextInput
                    value={branch}
                    onChange={(e) => setBranch(e.target.value)}
                    disabled={starting}
                    placeholder="main"
                />
            </Field>

            <Field label="Revision" description="A commit, tag or other git revision; the tip of the branch when empty.">
                <TextInput
                    value={revision}
                    onChange={(e) => setRevision(e.target.value)}
                    disabled={starting}
                    placeholder="Optional"
                />
            </Field>

            {state.status === "failed" && (
                <Alert tone="error" title="The scan could not be started" announce>
                    {state.message}
                </Alert>
            )}

            <div className="flex justify-end gap-2">
                <Button variant="ghost" size="sm" onClick={onDecline} disabled={starting}>
                    Decline
                </Button>
                <Button
                    variant="primary"
                    size="sm"
                    loading={starting}
                    disabled={!branch.trim()}
                    onClick={() => onApprove({ branch: branch.trim(), revision: revision.trim() || undefined })}
                >
                    Start scan
                </Button>
            </div>
        </section>
    );
}
