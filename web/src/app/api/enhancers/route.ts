import { forwardApi, graphnousApi } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * Every enhancer installed on the server, in the order they run.
 */
export async function GET() {
    return forwardApi(() => graphnousApi().server.listEnhancersRaw(), "list enhancers");
}
