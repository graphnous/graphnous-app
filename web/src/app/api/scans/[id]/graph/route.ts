import { forwardApi, graphnousApi, queryOf } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * The scan's graph, from ?focus=...&depth=...&layers=a,b; layers empty
 * shows no layer, and missing shows every one.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: scanId } = await params;
    const query = queryOf(request);

    return forwardApi(
        () => graphnousApi().scans.getScanGraphRaw({
            scanId,
            focus: query.string("focus"),
            depth: query.integer("depth"),
            // Also when empty, which shows no layer
            layers: query.list("layers") ?? undefined,
        }),
        "get scan graph",
    );
}
