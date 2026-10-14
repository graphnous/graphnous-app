import { forwardApi, graphnousApi } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * The stats of the system: those of the latest scan that has any of each
 * of its projects, added up, with how many projects it has and how many
 * scans they have.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: systemId } = await params;

    return forwardApi(() => graphnousApi().systems.getSystemStatsRaw({ systemId }), "get system stats");
}
