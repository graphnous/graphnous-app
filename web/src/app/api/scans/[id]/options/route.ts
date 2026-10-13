import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * What the scan was created with besides its source: { enhancers }, the
 * names of the ones chosen for it, or null when none were, which ran every
 * installed enhancer; and { excludes }, the patterns of the files it left
 * out.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id } = await params;

    return forward(
        () => graphnousFetch(
            `/api/v1/scans/${encodeURIComponent(id)}/options`,
            request.headers.get("authorization"),
        ),
        "get scan options",
    );
}
