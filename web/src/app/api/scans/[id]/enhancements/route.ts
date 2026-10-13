import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * Enhances a completed scan again, in the background: { enhancers? }, the
 * names of the enhancers to run: all of them without, none when empty. 202 with the scan;
 * 409 when the scan is not completed, is already being enhanced or kept no
 * results to enhance.
 */
export async function POST(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id } = await params;
    const body = await request.text();

    return forward(
        () => graphnousFetch(
            `/api/v1/scans/${encodeURIComponent(id)}/enhancements`,
            request.headers.get("authorization"),
            body
                ? { method: "POST", headers: { "Content-Type": "application/json" }, body }
                : { method: "POST" },
        ),
        "enhance scan",
    );
}
