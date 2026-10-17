import type { Notification } from "@/types";

/**
 * When the sample notifications are seen: now, to the minute, so their
 * times read the same in the list, which takes it, and in the detail,
 * which reads the clock.
 */
export const NOW = new Date(Math.floor(Date.now() / 60_000) * 60_000);

const minutesAgo = (minutes: number) => new Date(NOW.getTime() - minutes * 60_000).toISOString();

const systemId = "6a1d0c3e-2f4b-4d7a-9c1e-5b8f0a2d3c4e";
const projectId = "235ab1b0-df86-469d-bf07-2e07c31bdb44";

/**
 * Notifications as a system has them, newest first: a few unread.
 */
export const NOTIFICATIONS: Notification[] = [
  {
    id: "n1",
    systemId,
    projectId,
    scanId: "7d17d5e9-de33-4fd0-b9ac-16daa040ed92",
    title: "Scan failed",
    content: "The scan of Graphnous App (branch orders-v2, revision 6abd1b1) failed: Checking out 6abd1b1 failed with exit code 128.",
    read: false,
    createdAt: minutesAgo(4),
  },
  {
    id: "n2",
    systemId,
    projectId,
    scanId: "7cd9c39c-8e0c-4e89-a07d-1b6a16ebea59",
    title: "Scan completed",
    content: "The scan of Graphnous App (branch main, revision a0c13cd) completed.",
    read: false,
    createdAt: minutesAgo(38),
  },
  {
    id: "n3",
    systemId,
    projectId,
    scanId: "7cd9c39c-8e0c-4e89-a07d-1b6a16ebea59",
    title: "Scan started",
    content: "The scan of Graphnous App (branch main, revision a0c13cd) started.",
    read: true,
    createdAt: minutesAgo(41),
  },
  {
    id: "n4",
    systemId,
    projectId,
    scanId: null,
    title: "Scans deleted",
    content: "Deleted 3 scans of Graphnous App, as they were older than 30 days.",
    read: true,
    createdAt: minutesAgo(60 * 26),
  },
];
