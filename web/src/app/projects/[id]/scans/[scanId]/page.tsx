"use client";

import { useParams } from "next/navigation";

import { ScanDetails } from "@/components/ScanDetails/ScanDetails";

/**
 * The scan as its own page shows it, reached through its project.
 */
export default function ProjectScanPage() {
    const { scanId } = useParams<{ id: string; scanId: string }>();

    return <ScanDetails scanId={scanId} via="project" />;
}
