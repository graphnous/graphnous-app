import {
    BASE_PATH,
    ChatApi,
    Configuration,
    FetchError,
    ProjectsApi,
    RequiredError,
    ResponseError,
    ScanCodeApi,
    ScansApi,
    ServerApi,
    SystemsApi,
} from "@/generated/api";
import { NextResponse, type NextRequest } from "next/server";

/**
 * The API of graphnous-server: NEXT_PUBLIC_GRAPHNOUS_API_URL (such as in
 * .env.local), or else the server in the spec.
 */
export const graphnousApiUrl = (process.env.NEXT_PUBLIC_GRAPHNOUS_API_URL || BASE_PATH).replace(/\/+$/, "");

/**
 * Calls the API, for what the generated client does not do, such as
 * following an event stream; use graphnousApi otherwise.
 */
export function graphnousFetch(
    path: string,
    init: RequestInit = {},
): Promise<Response> {
    const headers = new Headers(init.headers);

    return fetch(`${graphnousApiUrl}${path}`, { ...init, headers, cache: "no-store" });
}

/**
 * The API's response passed on as it is, status and body; 502 when the API
 * could not be reached.
 */
export async function forward(call: () => Promise<Response>, action: string): Promise<NextResponse> {
    try {
        const response = await call();
        const body = response.status === 204 ? null : await response.text();

        return new NextResponse(body || null, {
            status: response.status,
            headers: { "Content-Type": response.headers.get("Content-Type") ?? "application/json" },
        });
    } catch (error) {
        console.error(`Failed to ${action}:`, error);

        return NextResponse.json(
            { code: "NETWORK_ERROR", message: "The Graphnous API could not be reached." },
            { status: 502 },
        );
    }
}

/**
 * The generated clients of the API. Nothing is cached, as with
 * graphnousFetch.
 */
export function graphnousApi() {
    const configuration = new Configuration({
        basePath: graphnousApiUrl,
        fetchApi: (input, init) => fetch(input, { ...init, cache: "no-store" }),
    });

    return {
        chat: new ChatApi(configuration),
        projects: new ProjectsApi(configuration),
        scanCode: new ScanCodeApi(configuration),
        scans: new ScansApi(configuration),
        server: new ServerApi(configuration),
        systems: new SystemsApi(configuration),
    };
}

/**
 * The API's response to a call of a generated client's Raw method passed on
 * as it is, status and body, as forward does: an error of the API too, so
 * callers see why. 400 when the call lacks what the API requires, and 502
 * when the API could not be reached.
 */
export async function forwardApi(call: () => Promise<{ raw: Response }>, action: string): Promise<NextResponse> {
    try {
        return await passOn((await call()).raw);
    } catch (error) {
        if (error instanceof ResponseError) {
            return passOn(error.response);
        }

        if (error instanceof RequiredError) {
            return NextResponse.json({ code: "VALIDATION_ERROR", message: error.message }, { status: 400 });
        }

        console.error(`Failed to ${action}:`, error);

        return error instanceof FetchError
            ? NextResponse.json({ code: "NETWORK_ERROR", message: "The Graphnous API could not be reached." }, { status: 502 })
            : NextResponse.json({ code: "INTERNAL_ERROR", message: `Failed to ${action}.` }, { status: 500 });
    }
}

async function passOn(response: Response): Promise<NextResponse> {
    const body = response.status === 204 ? null : await response.text();

    return new NextResponse(body || null, {
        status: response.status,
        headers: { "Content-Type": response.headers.get("Content-Type") ?? "application/json" },
    });
}

/**
 * The query parameters of a request, as the generated clients take them:
 * what is missing or not well formed is left out, so the API uses its
 * default.
 */
export function queryOf(request: NextRequest) {
    const parameters = request.nextUrl.searchParams;

    return {
        string(name: string): string | undefined {
            return parameters.get(name) || undefined;
        },
        integer(name: string): number | undefined {
            const value = parameters.get(name);
            const number = value === null || value.trim() === "" ? NaN : Number(value);

            return Number.isInteger(number) ? number : undefined;
        },
        boolean(name: string): boolean | undefined {
            const value = parameters.get(name);

            return value === "true" ? true : value === "false" ? false : undefined;
        },
        /**
         * The value of a parameter the API takes only some values of, such
         * as a sort, typed as the enum of those: passed on as it is, so the
         * API answers 400 for one it does not take.
         */
        // eslint-disable-next-line @typescript-eslint/no-unused-vars -- the enum types the value
        oneOf<T extends string>(name: string, allowed: Record<string, T>): T | undefined {
            return (parameters.get(name) || undefined) as T | undefined;
        },
        /**
         * The values of a list written once, comma separated, such as
         * scans=a,b; null when it is missing, empty when it is empty.
         */
        list(name: string): string[] | null {
            const value = parameters.get(name);

            return value === null ? null : value.split(",").map((item) => item.trim()).filter(Boolean);
        },
    };
}
