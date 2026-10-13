import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { NextResponse, type NextRequest } from "next/server";

/**
 * What of a scan can be listed, a page at a time.
 */
const LISTINGS = ["modules", "packages", "files", "classes", "methods"];

/**
 * A page of what the scan found of one kind: its modules, packages, files,
 * classes or methods. Every parameter is passed on as it is: ?query, the
 * filters of the kind, page, size, sort and direction. The API answers 400
 * for a filter, sort or size it does not take.
 */
export async function GET(
    request: NextRequest,
    { params }: { params: Promise<{ id: string; listing: string }> },
) {
    const { id, listing } = await params;

    if (!LISTINGS.includes(listing)) {
        return NextResponse.json({ code: "NOT_FOUND", message: "No such listing of a scan." }, { status: 404 });
    }

    const search = request.nextUrl.searchParams.size > 0 ? `?${request.nextUrl.searchParams}` : "";

    return forward(
        () => graphnousFetch(
            `/api/v1/scans/${encodeURIComponent(id)}/${listing}${search}`,
            request.headers.get("authorization"),
        ),
        `get scan ${listing}`,
    );
}
