import { forwardApi, graphnousApi } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * What was added, removed and changed from the scan to the head scan.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string; headId: string }> }) {
    const { id: baseScanId, headId: headScanId } = await params;

    return forwardApi(() => graphnousApi().scans.compareScansRaw({ baseScanId, headScanId }), "compare scans");
}
