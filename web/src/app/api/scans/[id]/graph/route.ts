import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * The part of the scan's graph around a node: ?focus=<node id>&depth=<hops>,
 * each optional. Without a focus it is around the scan itself; the API
 * answers 404 for a focus that is not in the scan's graph and 400 for a
 * depth out of range.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id } = await params;

    const query = new URLSearchParams();

    for (const name of ["focus", "depth"]) {
        const value = request.nextUrl.searchParams.get(name);

        if (value) {
            query.set(name, value);
        }
    }

    const search = query.size > 0 ? `?${query}` : "";

    return forward(
        () => graphnousFetch(
            `/api/v1/scans/${encodeURIComponent(id)}/graph${search}`,
            request.headers.get("authorization"),
        ),
        "get scan graph",
    );
}
