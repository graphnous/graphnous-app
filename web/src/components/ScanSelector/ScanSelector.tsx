"use client";

import { Combobox, formatRelative, type ComboboxProps } from "@graphnous/theme";

import { revisionLabel } from "@/components/ScanRevision/ScanRevision";
import type { Scan } from "@/types";

export type ScanSelectorProps = Omit<ComboboxProps, "options" | "value" | "onValueChange"> & {
  scans: Scan[];
  /**
   * The id of the chosen scan; null while none is chosen.
   */
  value: string | null;
  /**
   * Called with the chosen scan.
   */
  onScanChange: (scan: Scan) => void;
};

/**
 * Only a completed scan has results to show.
 */
export function completedScans(scans: Scan[]): Scan[] {
  return scans
    .filter((scan) => scan.status === "COMPLETED")
    .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
}

/**
 * Choosing one of a project's completed scans, the newest first, in a
 * Field, by typing to narrow the list down: each option shows the scan's
 * branch and revision, and when it was made below it.
 */
export function ScanSelector({
  scans,
  value,
  onScanChange,
  placeholder = "Choose a scan",
  emptyMessage = "No scans match",
  ...props
}: ScanSelectorProps) {
  const completed = completedScans(scans);

  const options = completed.map((scan) => ({
    value: scan.id,
    label: [scan.branch, revisionLabel(scan)].filter(Boolean).join(" · "),
    description: formatRelative(new Date(scan.createdAt)),
  }));

  return (
    <Combobox
      {...props}
      options={options}
      value={value}
      placeholder={placeholder}
      emptyMessage={emptyMessage}
      onValueChange={(id) => {
        const scan = completed.find((candidate) => candidate.id === id);

        if (scan) {
          onScanChange(scan);
        }
      }}
    />
  );
}
