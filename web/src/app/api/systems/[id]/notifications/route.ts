import { forwardApi, graphnousApi, queryOf } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * A page of the system's notifications, newest first: ?page, size and
 * unread=true for only the unread ones, each optional.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: systemId } = await params;
    const query = queryOf(request);

    return forwardApi(
        () => graphnousApi().notifications.listSystemNotificationsRaw({
            systemId,
            page: query.integer("page"),
            size: query.integer("size"),
            unread: query.boolean("unread"),
        }),
        "list notifications",
    );
}
