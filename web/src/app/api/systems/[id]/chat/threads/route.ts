import { forwardApi, graphnousApi, queryOf } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * The caller's conversations about the system, the last one talked in
 * first: ?page=0&size=20, each optional.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: systemId } = await params;
    const query = queryOf(request);

    return forwardApi(
        () => graphnousApi().chat.listSystemChatThreadsRaw({
            systemId,
            page: query.integer("page"),
            size: query.integer("size"),
        }),
        "list conversations",
    );
}

/**
 * Starts a conversation about the system.
 */
export async function POST(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: systemId } = await params;

    return forwardApi(
        () => graphnousApi().chat.createSystemChatThreadRaw({ systemId }),
        "start a conversation",
    );
}
