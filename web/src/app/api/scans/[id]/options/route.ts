import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * What the scan was created with besides its source: { enhancers }, the
 * names of the ones chosen for it, or null when none were, which ran every
 * installed enhancer; { excludes }, the patterns of the files it left
 * out; and { languages }, the ones it scanned, none when it scanned every
 * language it found.
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
