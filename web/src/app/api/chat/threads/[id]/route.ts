import { forwardApi, graphnousApi } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * The conversation with its messages.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: threadId } = await params;

    return forwardApi(() => graphnousApi().chat.getChatThreadRaw({ threadId }), "get conversation");
}

/**
 * Deletes the conversation.
 */
export async function DELETE(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: threadId } = await params;

    return forwardApi(() => graphnousApi().chat.deleteChatThreadRaw({ threadId }), "delete conversation");
}
