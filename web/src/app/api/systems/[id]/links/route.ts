import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * How the system's projects are linked over HTTP: which HTTP calls of
 * their scans request which endpoints. ?scans=<scan ids, by commas> links
 * those scans, such as the ones a graph shows; the latest completed scan
 * of each project without it.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id } = await params;

    const scans = request.nextUrl.searchParams.get("scans");
    const search = scans ? `?${new URLSearchParams({ scans })}` : "";

    return forward(
        () => graphnousFetch(
            `/api/v1/systems/${encodeURIComponent(id)}/links${search}`,
            request.headers.get("authorization"),
        ),
        "get system links",
    );
}
