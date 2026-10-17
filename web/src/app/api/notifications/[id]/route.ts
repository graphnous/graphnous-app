import { forwardApi, graphnousApi } from "@/lib/server/graphnousApi";
import { NextResponse, type NextRequest } from "next/server";

/**
 * The notification, read or not as the user read it.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: notificationId } = await params;

    return forwardApi(() => graphnousApi().notifications.getNotificationRaw({ notificationId }), "get notification");
}

/**
 * Marks the notification read or unread for the user, from { read }; 400
 * without read.
 */
export async function PATCH(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: notificationId } = await params;
    let body: unknown;

    try {
        body = await request.json();
    } catch {
        return NextResponse.json({ code: "MALFORMED_REQUEST", message: "The body must be JSON." }, { status: 400 });
    }

    const { read } = (typeof body === "object" && body !== null ? body : {}) as Record<string, unknown>;

    if (typeof read !== "boolean") {
        return NextResponse.json({ code: "VALIDATION_ERROR", message: "read must be true or false." }, { status: 400 });
    }

    return forwardApi(
        () => graphnousApi().notifications.updateNotificationRaw({ notificationId, updateNotificationRequest: { read } }),
        "update notification",
    );
}

/**
 * Deletes the notification; 204 when it is gone.
 */
export async function DELETE(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: notificationId } = await params;

    return forwardApi(() => graphnousApi().notifications.deleteNotificationRaw({ notificationId }), "delete notification");
}
