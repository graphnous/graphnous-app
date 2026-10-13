import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * Every enhancer installed on the server, in the order they run.
 */
export async function GET(request: NextRequest) {
    return forward(
        () => graphnousFetch("/api/v1/enhancers", request.headers.get("authorization")),
        "list enhancers",
    );
}
