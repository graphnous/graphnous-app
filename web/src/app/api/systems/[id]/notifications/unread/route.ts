import { forwardApi, graphnousApi } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * How many of the system's notifications the user has not read.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: systemId } = await params;

    return forwardApi(
        () => graphnousApi().notifications.countUnreadNotificationsRaw({ systemId }),
        "count unread notifications",
    );
}
