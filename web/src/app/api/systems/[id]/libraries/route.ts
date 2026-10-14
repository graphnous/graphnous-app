import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

const PARAMETERS = ["query", "drift", "page", "size", "sort", "direction"];

/**
 * A page of the libraries the system depends on, each once with every
 * version its projects use: ?query filters them by name, ?drift=true keeps
 * those used in more than one version, and page, size, sort (name or
 * versions) and direction page through them, each optional.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id } = await params;

    const query = new URLSearchParams();

    for (const name of PARAMETERS) {
        const value = request.nextUrl.searchParams.get(name);

        if (value) {
            query.set(name, value);
        }
    }

    const search = query.size > 0 ? `?${query}` : "";

    return forward(
        () => graphnousFetch(
            `/api/v1/systems/${encodeURIComponent(id)}/libraries${search}`,
            request.headers.get("authorization"),
        ),
        "get system libraries",
    );
}
