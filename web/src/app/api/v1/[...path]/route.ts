import { graphnousFetch } from "@/lib/server/graphnousApi";
import { NextResponse, type NextRequest } from "next/server";

/**
 * The API's reads, for the generated client in the browser (useApi), which
 * calls the web app: passed on to the server as they are, status and body,
 * and an event stream as it comes, such as a scan's live log. Writes go
 * through the app's own routes.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
    const { path } = await params;

    try {
        const response = await graphnousFetch(`/api/v1/${path.map(encodeURIComponent).join("/")}${request.nextUrl.search}`, {
            headers: { Accept: request.headers.get("Accept") ?? "application/json" },
            // Ends the API's response, such as a stream, when the browser's ends
            signal: request.signal,
        });

        const contentType = response.headers.get("Content-Type") ?? "application/json";

        return new Response(response.body, {
            status: response.status,
            headers: contentType.startsWith("text/event-stream")
                ? { "Content-Type": contentType, "Cache-Control": "no-cache, no-transform", Connection: "keep-alive" }
                : { "Content-Type": contentType },
        });
    } catch (error) {
        console.error(`Failed to read /api/v1/${path.join("/")}:`, error);

        return NextResponse.json(
            { code: "NETWORK_ERROR", message: "The Graphnous API could not be reached." },
            { status: 502 },
        );
    }
}
