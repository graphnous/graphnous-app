import { forwardApi, graphnousApi } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * The scan; 404 when it is not one of the caller's.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: scanId } = await params;

    return forwardApi(() => graphnousApi().scans.getScanRaw({ scanId }), "get scan");
}

/**
 * Deletes the scan with its results, logs and steps; 204 when it is gone,
 * 409 while it is still active.
 */
export async function DELETE(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: scanId } = await params;

    return forwardApi(
        () => graphnousApi().scans.deleteScanRaw({ scanId }),
        "delete scan",
    );
}
