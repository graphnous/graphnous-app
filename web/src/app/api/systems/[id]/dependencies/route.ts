import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

const PARAMETERS = ["query", "module", "scope", "page", "size", "sort", "direction"];

/**
 * A page of the libraries the system's projects depend on, those of the
 * latest completed scan of each: ?query, module and scope filter them,
 * page, size, sort and direction page through them, each optional. The API
 * answers 400 for a sort or size it does not take, and 404 for a system
 * that is not the caller's.
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
            `/api/v1/systems/${encodeURIComponent(id)}/dependencies${search}`,
            request.headers.get("authorization"),
        ),
        "get system dependencies",
    );
}
