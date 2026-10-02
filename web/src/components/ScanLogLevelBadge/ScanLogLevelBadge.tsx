import { Badge, type BadgeTone } from "@graphnous/theme";

import type { ScanLogLevel } from "@/types";

/**
 * The tones the LogViewer gives each level's lines.
 */
const tones: Record<ScanLogLevel, BadgeTone> = {
  TRACE: "neutral",
  DEBUG: "neutral",
  INFO: "info",
  WARN: "warning",
  ERROR: "error",
};

export type ScanLogLevelBadgeProps = {
  level: ScanLogLevel;
  className?: string;
};

/**
 * The level of a scan's log line, such as for filtering the log.
 */
export function ScanLogLevelBadge({ level, className }: ScanLogLevelBadgeProps) {
  return (
    <Badge tone={tones[level]} className={className}>
      {level}
    </Badge>
  );
}
