import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * The stats of the project's latest scan that has any; all zero when none
 * has, and 404 for a project that is not the caller's.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id } = await params;

    return forward(
        () => graphnousFetch(
            `/api/v1/projects/${encodeURIComponent(id)}/stats`,
            request.headers.get("authorization"),
        ),
        "get project stats",
    );
}
