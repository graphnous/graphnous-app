"use client";

import { Spinner } from "@graphnous/theme";
import { useState } from "react";
import { z } from "zod";

import { ScanApproval, type ScanApprovalState } from "@/components/ScanApproval/ScanApproval";
import { useGetProject } from "@/lib/hooks/project/useGetProject";
import { useCreateScan } from "@/lib/hooks/scan/useCreateScan";

export const startScanParameters = z.object({
    projectId: z.string().describe("The id of the project to scan, from listProjects"),
    branch: z.string().describe("The branch to scan, e.g. main or the branch the user works on"),
    revision: z
        .string()
        .optional()
        .describe("A commit, tag or other git revision of the branch; its tip when left out"),
    reason: z
        .string()
        .optional()
        .describe("Why to scan, in a sentence for the user, e.g. to compare the branch with main"),
});

export type StartScanParameters = z.infer<typeof startScanParameters>;

/**
 * What the assistant learns of the user's answer.
 */
export type StartScanResult =
    | { started: true; scanId: string; projectId: string; branch: string; revision?: string }
    | { started: false; reason: string };

export type StartScanCallProps = {
    toolCallId: string;
    args: Partial<StartScanParameters>;
    status: "inProgress" | "executing" | "complete";
    /**
     * Answers the assistant, while the call is executing.
     */
    respond?: (result: unknown) => Promise<void>;
    /**
     * What it was answered, once complete.
     */
    result?: string;
};

/**
 * The assistant's request to scan a project, which waits for the user:
 * only their click starts the scan, as them, through the app's own API.
 */
export function StartScanCall({ args, status, respond, result }: StartScanCallProps) {
    const { project } = useGetProject(args.projectId ?? "");
    const { createScan } = useCreateScan({ projectId: args.projectId ?? "" });
    const [state, setState] = useState<ScanApprovalState>({ status: "pending" });

    if (status === "inProgress" || !args.projectId) {
        return (
            <div className="my-2 flex items-center gap-2 text-sm text-foreground-muted">
                <Spinner size="sm" label="Preparing a scan" />
                <span>Preparing a scan…</span>
            </div>
        );
    }

    const projectId = args.projectId;
    const answered = status === "complete" ? answer(result) : null;
    const shown: ScanApprovalState = answered
        ? answered.started
            ? { status: "started", scanId: answered.scanId, branch: answered.branch, revision: answered.revision }
            : { status: "declined" }
        : state;

    const approve = async ({ branch, revision }: { branch: string; revision?: string }) => {
        setState({ status: "starting" });

        try {
            const scan = await createScan({ branch, revision, trigger: "CHAT" });

            setState({ status: "started", scanId: scan.id, branch, revision });
            await respond?.({ started: true, scanId: scan.id, projectId, branch, revision } satisfies StartScanResult);
        } catch (error) {
            // The card stays, so the user can try again or decline
            setState({ status: "failed", message: error instanceof Error ? error.message : "Failed to create scan" });
        }
    };

    const decline = async () => {
        setState({ status: "declined" });
        await respond?.({ started: false, reason: "The user declined to start the scan" } satisfies StartScanResult);
    };

    return (
        <ScanApproval
            projectId={projectId}
            projectName={project?.name}
            branch={args.branch}
            revision={args.revision}
            reason={args.reason}
            state={status === "complete" && !answered ? { status: "declined" } : shown}
            onApprove={approve}
            onDecline={decline}
        />
    );
}

/**
 * The answer the call completed with, as respond sent it.
 */
function answer(result: string | undefined): StartScanResult | null {
    if (!result) {
        return null;
    }

    try {
        const parsed = JSON.parse(result) as Partial<StartScanResult>;

        if (parsed.started === true && typeof parsed.scanId === "string" && typeof parsed.branch === "string") {
            return parsed as StartScanResult;
        }

        if (parsed.started === false) {
            return { started: false, reason: typeof parsed.reason === "string" ? parsed.reason : "" };
        }
    } catch {
        // Not an answer of this card
    }

    return null;
}
