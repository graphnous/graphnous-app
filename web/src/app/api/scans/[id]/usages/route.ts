import { forwardApi, graphnousApi, queryOf } from "@/lib/server/graphnousApi";
import { NextResponse, type NextRequest } from "next/server";

/**
 * What uses a node of the scan, from ?node=...&depth=...: what a change to
 * it affects, also over HTTP from other projects.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: scanId } = await params;
    const query = queryOf(request);
    const node = query.string("node");

    if (!node) {
        return NextResponse.json({ code: "VALIDATION_ERROR", message: "node is required" }, { status: 400 });
    }

    return forwardApi(
        () => graphnousApi().scans.getScanUsagesRaw({
            scanId,
            node,
            depth: query.integer("depth"),
        }),
        "get scan usages",
    );
}
