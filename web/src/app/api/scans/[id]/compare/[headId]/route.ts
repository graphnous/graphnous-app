import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * How the head scan's graph differs from the base scan's, the scan of the
 * path; the API answers 404 when either scan is not there.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string; headId: string }> }) {
    const { id, headId } = await params;

    return forward(
        () => graphnousFetch(
            `/api/v1/scans/${encodeURIComponent(id)}/compare/${encodeURIComponent(headId)}`,
            request.headers.get("authorization"),
        ),
        "compare scans",
    );
}
