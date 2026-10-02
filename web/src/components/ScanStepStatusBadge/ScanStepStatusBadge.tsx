import type { Icon as PhosphorIcon } from "@phosphor-icons/react";
import { MinusIcon } from "@phosphor-icons/react/ssr";
import { StatusIndicator, type BadgeTone } from "graphnous-theme";

import type { ScanStepStatus } from "@/types";

const statuses: Record<ScanStepStatus, { label: string; tone: BadgeTone; active?: boolean; icon?: PhosphorIcon }> = {
  PENDING: { label: "Pending", tone: "neutral" },
  RUNNING: { label: "Running", tone: "info", active: true },
  COMPLETED: { label: "Completed", tone: "success" },
  FAILED: { label: "Failed", tone: "error" },
  // A dash instead of the dot, so it does not read as pending
  SKIPPED: { label: "Skipped", tone: "neutral", icon: MinusIcon },
};

export type ScanStepStatusBadgeProps = {
  status: ScanStepStatus;
  className?: string;
};

/**
 * A scan step's status, in words and its tone; a running step's dot
 * pulses, and a skipped step shows a dash, as it did not run.
 */
export function ScanStepStatusBadge({ status, className }: ScanStepStatusBadgeProps) {
  const { label, tone, active, icon } = statuses[status];

  return (
    <StatusIndicator tone={tone} active={active} icon={icon} className={className}>
      {label}
    </StatusIndicator>
  );
}
