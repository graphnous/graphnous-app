import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * The stats of the system: those of the latest scan that has any of each
 * of its projects, added up. All zero before any of its projects completed
 * a scan; 404 for a system that is not the caller's.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id } = await params;

    return forward(
        () => graphnousFetch(
            `/api/v1/systems/${encodeURIComponent(id)}/stats`,
            request.headers.get("authorization"),
        ),
        "get system stats",
    );
}
