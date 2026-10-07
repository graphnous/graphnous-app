import { BASE_PATH } from "@/generated/api";
import { NextResponse } from "next/server";

/**
 * The API of graphnous-server: NEXT_PUBLIC_GRAPHNOUS_API_URL (such as in
 * .env.local), or else the server in the spec.
 */
export const graphnousApiUrl = (process.env.NEXT_PUBLIC_GRAPHNOUS_API_URL || BASE_PATH).replace(/\/+$/, "");

/**
 * Calls the API as the caller: their access token, when they sent one, goes
 * along, as the server checks it when security is on. For the endpoints the
 * generated client does not have yet.
 */
export function graphnousFetch(
    path: string,
    authorization: string | null | undefined,
    init: RequestInit = {},
): Promise<Response> {
    const headers = new Headers(init.headers);

    if (authorization) {
        headers.set("Authorization", authorization);
    }

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
