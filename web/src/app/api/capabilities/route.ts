import {
    BASE_PATH,
    CapabilitiesApi,
    Configuration,
    FetchError,
    ResponseError,
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
function capabilitiesApi(request: NextRequest): CapabilitiesApi {
    return new CapabilitiesApi(
        new Configuration({
            basePath,
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
 * The value when it is one of the allowed ones.
 */
function oneOf<T extends string>(allowed: Record<string, T>, value: string): T | undefined {
    return Object.values(allowed).find((option) => option === value);
}

/**
 * Lists systems, a page at a time: ?page=0&size=20&sort=name&direction=asc,
 * each optional.
 */
export async function GET(request: NextRequest) {
    try {
        const capabilities = await capabilitiesApi(request).getCapabilities();

        return NextResponse.json({
            capabilities: [...capabilities.capabilities],
            authorization: capabilities.authorization
        });
    } catch (error) {
        return errorResponse(error, "capabilities");
    }
}
