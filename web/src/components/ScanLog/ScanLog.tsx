"use client";

import { useEffect, useState } from "react";
import { Field, Inline, LogViewer, Select, Skeleton, Stack, StatusIndicator, type SelectOption } from "@graphnous/theme";

import type { ScanLog as ApiScanLog } from "@/generated/api";
import { ApiErrorMessage } from "@/components/ApiErrorMessage/ApiErrorMessage";
import { useApi } from "@/lib/api/client";
import { useEventStream } from "@/lib/hooks/useEventStream";
import type { Scan, ScanLog as ScanLogLine, ScanLogLevel, ScanStatus } from "@/types";

/**
 * The statuses of a scan that may still write to its log.
 */
const activeStatuses: ScanStatus[] = ["PENDING", "QUEUED", "RUNNING"];

/**
 * The most lines the API returns at once.
 */
const pageSize = 1000;

const levels: ScanLogLevel[] = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"];

/**
 * Each option shows the lines of its level and the levels above it.
 */
const levelOptions: SelectOption[] = [
  { value: "TRACE", label: "All levels" },
  { value: "DEBUG", label: "Debug and above" },
  { value: "INFO", label: "Info and above" },
  { value: "WARN", label: "Warnings and errors" },
  { value: "ERROR", label: "Errors" },
];

function fromApi(log: ApiScanLog): ScanLogLine {
  return { ...log, timestamp: log.timestamp.toISOString() };
}

/**
 * The lines of both, once each, in the order of the log: the pages and the
 * live stream can both have a line.
 */
function merge(lines: ScanLogLine[], incoming: ScanLogLine[]): ScanLogLine[] {
  const bySequence = new Map(lines.map((line) => [line.sequence, line]));

  for (const line of incoming) {
    bySequence.set(line.sequence, line);
  }

  return [...bySequence.values()].sort((a, b) => a.sequence - b.sequence);
}

export type ScanLogProps = {
  scan: Pick<Scan, "id" | "status">;
  /**
   * The height of the log, as a CSS length.
   */
  height?: string;
  className?: string;
};

/**
 * A scan's log, filterable by level. It pages through the lines written so
 * far and, while the scan is active, follows the live stream of new ones.
 */
export function ScanLog({ scan, height, className }: ScanLogProps) {
  const api = useApi();
  const active = activeStatuses.includes(scan.status);

  const [log, setLog] = useState<{ scanId: string; lines: ScanLogLine[] }>({ scanId: scan.id, lines: [] });
  const [paging, setPaging] = useState<{ key: string; error?: unknown }>();
  const [attempt, setAttempt] = useState(0);
  const [minimum, setMinimum] = useState<ScanLogLevel>("TRACE");

  // A different scan starts from an empty log
  const lines = log.scanId === scan.id ? log.lines : [];

  // Pages through the log; again once the scan finishes, for any line
  // written before the stream was open
  const pagingKey = `${scan.id}:${active}:${attempt}`;

  useEffect(() => {
    const scanId = scan.id;
    const key = `${scanId}:${active}:${attempt}`;
    const controller = new AbortController();

    const load = async () => {
      try {
        for (let page = 0; ; page++) {
          const result = await api.scanLogs.listScanLogs({ scanId, page, size: pageSize }, { signal: controller.signal });

          setLog((current) => ({
            scanId,
            lines: merge(current.scanId === scanId ? current.lines : [], result.content.map(fromApi)),
          }));

          if (page + 1 >= result.totalPages) {
            break;
          }
        }

        setPaging({ key });
      } catch (caught) {
        if (!controller.signal.aborted) {
          setPaging({ key, error: caught });
        }
      }
    };

    load();

    return () => controller.abort();
  }, [api, scan.id, active, attempt]);

  const stream = useEventStream<ScanLogLine>(
    active ? `${api.baseUrl}/api/v1/scans/${encodeURIComponent(scan.id)}/logs/stream` : null,
    {
      event: "scanLog",
      onEvent: (line) =>
        setLog((current) => ({
          scanId: scan.id,
          lines: merge(current.scanId === scan.id ? current.lines : [], [line]),
        })),
    },
  );

  const loaded = paging?.key === pagingKey;
  const error = loaded ? paging.error : undefined;
  const visible = lines.filter((line) => levels.indexOf(line.level) >= levels.indexOf(minimum));

  return (
    <Stack gap={3} className={className}>
      <Inline justify="between" align="end" wrap gap={3}>
        <Field label="Level" className="w-56">
          <Select
            options={levelOptions}
            value={minimum}
            onChange={(event) => setMinimum(event.currentTarget.value as ScanLogLevel)}
          />
        </Field>
        {active &&
          (stream === "open" ? (
            <StatusIndicator tone="info" active>
              Live
            </StatusIndicator>
          ) : (
            <StatusIndicator tone="neutral">Connecting</StatusIndicator>
          ))}
      </Inline>
      {error ? <ApiErrorMessage error={error} onRetry={() => setAttempt((count) => count + 1)} /> : null}
      {!loaded && lines.length === 0 ? (
        <Skeleton shape="block" className="h-96" />
      ) : (
        <LogViewer label="Scan logs" lines={visible} height={height} />
      )}
    </Stack>
  );
}
