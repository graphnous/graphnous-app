import { forwardApi, graphnousApi } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * Every scanner the server runs, one per language it can scan.
 */
export async function GET(request: NextRequest) {
    return forwardApi(() => graphnousApi().server.listScannersRaw(), "list scanners");
}
