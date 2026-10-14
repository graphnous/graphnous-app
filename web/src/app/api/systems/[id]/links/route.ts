import { forwardApi, graphnousApi, queryOf } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * How the system's projects are linked over HTTP, from ?scans=a,b: the
 * latest completed scan of each project when missing.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: systemId } = await params;
    const scans = queryOf(request).list("scans");

    return forwardApi(
        () => graphnousApi().systems.getSystemLinksRaw({
            systemId,
            scans: scans && scans.length > 0 ? scans : undefined,
        }),
        "get system links",
    );
}
