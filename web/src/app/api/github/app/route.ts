import { forward, graphnousFetch } from "@/lib/server/graphnousApi";

/**
 * The GitHub App the server is, as GitHub has it; 404 when the server has
 * the App off.
 */
export async function GET() {
    return forward(() => graphnousFetch("/api/v1/github/app"), "get the GitHub App");
}

/**
 * Forgets the App setup registered; 409 when the server's configuration
 * names it.
 */
export async function DELETE() {
    return forward(() => graphnousFetch("/api/v1/github/app", { method: "DELETE" }), "disconnect the GitHub App");
}
