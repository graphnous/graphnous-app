import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * Every scanner the server runs, one per language it can scan.
 */
export async function GET(request: NextRequest) {
    return forward(
        () => graphnousFetch("/api/v1/scanners", request.headers.get("authorization")),
        "list scanners",
    );
}
