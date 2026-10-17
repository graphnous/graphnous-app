import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * The manifest of the GitHub App to register, and where to post it.
 */
export async function POST(request: NextRequest) {
    const body = await request.text();

    return forward(
        () => graphnousFetch("/api/v1/github/app/manifest", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body,
        }),
        "start the GitHub App's setup",
    );
}
