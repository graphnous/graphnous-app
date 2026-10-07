import {
    BASE_PATH,
    Configuration,
    FetchError,
    ResponseError,
    ScansApi,
} from "@/generated/api";
import { NextResponse, type NextRequest } from "next/server";

/**
 * The API of graphnous-server: NEXT_PUBLIC_GRAPHNOUS_API_URL (such as in
 * .env.local), or else the server in the spec.
 */
const basePath = (process.env.NEXT_PUBLIC_GRAPHNOUS_API_URL ?? BASE_PATH).replace(/\/+$/, "");

/**
 * A client that calls the API as the caller: their access token, when they
 * sent one, goes along, as the server checks it when security is on.
 */
function scansApi(request: NextRequest): ScansApi {
    const authorization = request.headers.get("authorization");

    return new ScansApi(
        new Configuration({
            basePath,
            headers: authorization ? { Authorization: authorization } : undefined,
        }),
    );
}

/**
 * Passes the API's error on as it is, status and body, so callers see why
 * (such as a scan that is still running); 502 when the API could not be
 * reached.
 */
async function errorResponse(error: unknown, action: string): Promise<NextResponse> {
    if (error instanceof ResponseError) {
        const body = await error.response.text();

        return new NextResponse(body || null, {
            status: error.response.status,
            headers: { "Content-Type": error.response.headers.get("Content-Type") ?? "application/json" },
        });
    }

    console.error(`Failed to ${action}:`, error);

    return error instanceof FetchError
        ? NextResponse.json({ code: "NETWORK_ERROR", message: "The Graphnous API could not be reached." }, { status: 502 })
        : NextResponse.json({ code: "INTERNAL_ERROR", message: `Failed to ${action}.` }, { status: 500 });
}

/**
 * Deletes the scan with its results, logs and steps; 204 when it is gone,
 * 409 while it is still active.
 */
export async function DELETE(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: scanId } = await params;

    try {
        await scansApi(request).deleteScan({ scanId });

        return new NextResponse(null, { status: 204 });
    } catch (error) {
        return errorResponse(error, "delete scan");
    }
}
