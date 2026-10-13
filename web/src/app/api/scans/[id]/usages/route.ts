import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * What uses a node of the scan: ?node=<node id>, a class, method or field,
 * or what an enhancer added to one, and ?depth=<1 to 3> to also follow
 * what calls that. The API answers 404 for a node the scan does not have.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id } = await params;

    const query = new URLSearchParams();

    for (const name of ["node", "depth"]) {
        const value = request.nextUrl.searchParams.get(name);

        if (value) {
            query.set(name, value);
        }
    }

    return forward(
        () => graphnousFetch(
            `/api/v1/scans/${encodeURIComponent(id)}/usages?${query}`,
            request.headers.get("authorization"),
        ),
        "get scan usages",
    );
}
