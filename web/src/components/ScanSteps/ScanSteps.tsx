import { Duration, Timeline, type TimelineStatus, type TimelineStep } from "@graphnous/theme";

import { ScanStepError } from "@/components/ScanStepError/ScanStepError";
import type { ScanStep, ScanStepStatus, ScanStepType } from "@/types";

/**
 * The steps in the order they run, with their title and what they do, as
 * in "as checking out the repository failed".
 */
const steps: Record<ScanStepType, { order: number; title: string; doing: string }> = {
  CHECKOUT: { order: 0, title: "Check out the repository", doing: "checking out the repository" },
  PLAN: { order: 1, title: "Find the targets", doing: "finding the targets to scan" },
  SCAN: { order: 2, title: "Scan the targets", doing: "scanning" },
  STORE: { order: 3, title: "Store the results", doing: "storing the results" },
  ENHANCE_RESULTS: { order: 4, title: "Enhance the results", doing: "enhancing the results" },
  ENHANCE_SCAN: { order: 5, title: "Enhance the scan", doing: "enhancing the scan" },
};

/**
 * The steps an uploaded scan skips: the results were scanned elsewhere.
 */
const scanningSteps: ScanStepType[] = ["CHECKOUT", "PLAN", "SCAN"];

const statuses: Record<ScanStepStatus, TimelineStatus> = {
  PENDING: "pending",
  RUNNING: "running",
  COMPLETED: "completed",
  FAILED: "failed",
  SKIPPED: "skipped",
};

/**
 * Why a skipped step did not run: an earlier step failed, or, for the
 * scanning steps of a scan without failures, its results were uploaded.
 */
function skipReason(step: ScanStep, before: ScanStep[]): string {
  const failed = before.find((earlier) => earlier.status === "FAILED");

  if (failed) {
    return `Did not run, as ${steps[failed.type].doing} failed.`;
  }

  if (scanningSteps.includes(step.type)) {
    return "Did not run: the results were scanned elsewhere and uploaded.";
  }

  return "Did not run.";
}

export type ScanStepsProps = {
  steps: ScanStep[];
  className?: string;
};

/**
 * A scan's steps in order, each with its status and how long it took, the
 * error of a failed step, and why a skipped step did not run.
 */
export function ScanSteps({ steps: scanSteps, className }: ScanStepsProps) {
  const ordered = [...scanSteps].sort((a, b) => steps[a.type].order - steps[b.type].order);

  const items: TimelineStep[] = ordered.map((step, index) => ({
    id: step.type,
    title: steps[step.type].title,
    status: statuses[step.status],
    meta:
      step.startedAt && step.status !== "SKIPPED" ? <Duration start={step.startedAt} end={step.finishedAt} /> : undefined,
    children:
      step.status === "FAILED" ? (
        <ScanStepError step={step} className="mt-1" />
      ) : step.status === "SKIPPED" ? (
        skipReason(step, ordered.slice(0, index))
      ) : undefined,
  }));

  return <Timeline label="Scan steps" steps={items} className={className} />;
}
