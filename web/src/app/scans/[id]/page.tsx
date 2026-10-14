"use client";

import { useParams } from "next/navigation";

import { ScanDetails } from "@/components/ScanDetails/ScanDetails";

export default function ScanPage() {
    const { id } = useParams<{ id: string }>();

    return <ScanDetails scanId={id} />;
}
