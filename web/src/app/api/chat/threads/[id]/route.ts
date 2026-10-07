import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * A conversation with its messages.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id } = await params;

    return forward(
        () => graphnousFetch(`/api/v1/chat/threads/${encodeURIComponent(id)}`, request.headers.get("authorization")),
        "get conversation",
    );
}

/**
 * Deletes a conversation; 204 when it is gone.
 */
export async function DELETE(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id } = await params;

    return forward(
        () => graphnousFetch(
            `/api/v1/chat/threads/${encodeURIComponent(id)}`,
            request.headers.get("authorization"),
            { method: "DELETE" },
        ),
        "delete conversation",
    );
}
