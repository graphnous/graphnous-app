import { StatusIndicator, type BadgeTone } from "@graphnous/theme";

import type { ScanStatus } from "@/types";

const statuses: Record<ScanStatus, { label: string; tone: BadgeTone; active?: boolean }> = {
  PENDING: { label: "Pending", tone: "neutral" },
  QUEUED: { label: "Queued", tone: "neutral" },
  RUNNING: { label: "Running", tone: "info", active: true },
  COMPLETED: { label: "Completed", tone: "success" },
  FAILED: { label: "Failed", tone: "error" },
};

export type ScanStatusBadgeProps = {
  status: ScanStatus;
  className?: string;
};

/**
 * A scan's status, in words and its tone; a running scan's dot pulses.
 */
export function ScanStatusBadge({ status, className }: ScanStatusBadgeProps) {
  const { label, tone, active } = statuses[status];

  return (
    <StatusIndicator tone={tone} active={active} className={className}>
      {label}
    </StatusIndicator>
  );
}
