import { forwardApi, graphnousApi } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * The stats of the project's latest scan that has any, with how many scans
 * the project has.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: projectId } = await params;

    return forwardApi(() => graphnousApi().projects.getProjectStatsRaw({ projectId }), "get project stats");
}
