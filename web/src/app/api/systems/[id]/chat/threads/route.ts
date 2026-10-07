import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * The caller's conversations about the system, the last one talked in
 * first: ?page=0&size=20, each optional.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: systemId } = await params;
    const query = new URLSearchParams();

    for (const name of ["page", "size"]) {
        const value = request.nextUrl.searchParams.get(name);

        if (value !== null) {
            query.set(name, value);
        }
    }

    return forward(
        () => graphnousFetch(
            `/api/v1/systems/${encodeURIComponent(systemId)}/chat/threads${query.size ? `?${query}` : ""}`,
            request.headers.get("authorization"),
        ),
        "list conversations",
    );
}

/**
 * Starts a conversation about the system; 201 with the new thread.
 */
export async function POST(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: systemId } = await params;

    return forward(
        () => graphnousFetch(
            `/api/v1/systems/${encodeURIComponent(systemId)}/chat/threads`,
            request.headers.get("authorization"),
            { method: "POST" },
        ),
        "start a conversation",
    );
}
