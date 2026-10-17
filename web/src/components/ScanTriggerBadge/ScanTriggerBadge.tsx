import { Badge } from "@graphnous/theme";
import { ChatCircleDotsIcon, GithubLogoIcon, HandPointingIcon, UploadSimpleIcon } from "@phosphor-icons/react";

import type { ScanTrigger } from "@/types";

const triggers: Record<ScanTrigger, { label: string; icon: typeof HandPointingIcon; description: string }> = {
  MANUAL: { label: "Manual", icon: HandPointingIcon, description: "Started in the app" },
  CHAT: { label: "Chat", icon: ChatCircleDotsIcon, description: "Started from the assistant's chat" },
  GITHUB_APP: { label: "GitHub App", icon: GithubLogoIcon, description: "Started by the GitHub App, for a push or a pull request" },
  UPLOAD: { label: "Upload", icon: UploadSimpleIcon, description: "Of results scanned elsewhere and uploaded" },
};

export type ScanTriggerBadgeProps = {
  /**
   * Null or missing for a scan from before triggers were recorded.
   */
  trigger?: ScanTrigger | null;
  className?: string;
};

/**
 * Why a scan started, as a label with its icon; a dash for a scan from
 * before triggers were recorded.
 */
export function ScanTriggerBadge({ trigger, className }: ScanTriggerBadgeProps) {
  if (!trigger) {
    return <span className="text-foreground-muted" aria-label="Unknown trigger">–</span>;
  }

  const { label, icon, description } = triggers[trigger];

  return (
    <span title={description} className={className}>
      <Badge tone="neutral" icon={icon}>{label}</Badge>
    </span>
  );
}
