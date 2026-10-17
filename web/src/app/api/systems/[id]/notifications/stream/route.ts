import { graphnousFetch } from "@/lib/server/graphnousApi";
import { NextResponse, type NextRequest } from "next/server";

/**
 * What happens to the system's notifications, as the API streams it:
 * server-sent events, passed on as they come. With a plain fetch, as the
 * generated client reads a response whole rather than as it streams.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: systemId } = await params;

    try {
        const response = await graphnousFetch(`/api/v1/systems/${encodeURIComponent(systemId)}/notifications/stream`, {
            headers: { Accept: "text/event-stream" },
            // Ends the API's stream when the browser's ends
            signal: request.signal,
        });

        if (!response.ok || !response.body) {
            return new NextResponse(await response.text().catch(() => null), {
                status: response.status || 502,
                headers: { "Content-Type": response.headers.get("Content-Type") ?? "application/json" },
            });
        }

        return new Response(response.body, {
            status: 200,
            headers: {
                "Content-Type": "text/event-stream",
                "Cache-Control": "no-cache, no-transform",
                Connection: "keep-alive",
            },
        });
    } catch (error) {
        console.error("Failed to stream notifications:", error);

        return NextResponse.json(
            { code: "NETWORK_ERROR", message: "The Graphnous API could not be reached." },
            { status: 502 },
        );
    }
}
