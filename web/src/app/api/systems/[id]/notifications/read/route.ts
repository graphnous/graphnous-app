import { forwardApi, graphnousApi } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * Marks every notification of the system read for the user; answers how
 * many were unread.
 */
export async function POST(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: systemId } = await params;

    return forwardApi(
        () => graphnousApi().notifications.markAllNotificationsReadRaw({ systemId }),
        "mark notifications read",
    );
}
