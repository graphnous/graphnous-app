import {
    BASE_PATH,
    Configuration,
    CreateProjectRequest,
    CreateScanRequest,
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

function badRequest(message: string): NextResponse {
    return NextResponse.json({ code: "VALIDATION_ERROR", message }, { status: 400 });
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
 * The steps of the scan, in the order they run.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string, scanId: string }> }) {
    const { id: projectId, scanId } = await params;

    try {
        const steps = await scansApi(request).getScanExecution({
            scanId
        });


        return NextResponse.json(steps);
    } catch (error) {
        return errorResponse(error, "get scan steps");
    }
}

