import {
    CapabilitiesApi,
    Configuration,
    FetchError,
    ResponseError,
} from "@/generated/api";
import { NextResponse, type NextRequest } from "next/server";
import { graphnousApiUrl } from "@/lib/server/graphnousApi";

/**
 * The API of graphnous-server, as graphnousApiUrl says.
 */
const basePath = graphnousApiUrl;

/**
 * A client that calls the API as the caller: their access token, when they
 * sent one, goes along, as the server checks it when security is on.
 */
function capabilitiesApi(): CapabilitiesApi {
    return new CapabilitiesApi(
        new Configuration({
            basePath,
            fetchApi: fetch
        }),
    );
}

/**
 * Passes the API's error on as it is, status and body, so callers see why
 * (such as a name already taken); 502 when the API could not be reached.
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
 * Lists systems, a page at a time: ?page=0&size=20&sort=name&direction=asc,
 * each optional.
 */
export async function GET() {
    try {
        const capabilities = await capabilitiesApi().getCapabilities();

        return NextResponse.json({
            capabilities: [...capabilities.capabilities],
            authorization: capabilities.authorization
        });
    } catch (error) {
        return errorResponse(error, "capabilities");
    }
}
