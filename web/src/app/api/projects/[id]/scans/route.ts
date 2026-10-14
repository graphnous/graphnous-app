import { ListScansDirectionEnum, ListScansSortEnum } from "@/generated/api";
import { forwardApi, graphnousApi, queryOf } from "@/lib/server/graphnousApi";
import { NextResponse, type NextRequest } from "next/server";

/**
 * A page of the project's scans: ?page, size, sort (createdAt or
 * updatedAt) and direction, each optional. The API answers 400 for a sort
 * or size it does not take.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: projectId } = await params;
    const query = queryOf(request);

    return forwardApi(
        () => graphnousApi().scans.listScansRaw({
            projectId,
            page: query.integer("page"),
            size: query.integer("size"),
            sort: query.oneOf("sort", ListScansSortEnum),
            direction: query.oneOf("direction", ListScansDirectionEnum),
        }),
        "list scans",
    );
}

/**
 * Creates a scan of the project from { branch, revision?, enhancers?,
 * excludes?, languages? }; 201 with the new scan, which runs in the
 * background.
 */
export async function POST(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: projectId } = await params;
    let body: unknown;

    try {
        body = await request.json();
    } catch {
        return NextResponse.json({ code: "MALFORMED_REQUEST", message: "The body must be JSON." }, { status: 400 });
    }

    const { branch, revision, enhancers, excludes, languages } =
        (typeof body === "object" && body !== null ? body : {}) as Record<string, unknown>;

    return forwardApi(
        () => graphnousApi().scans.createScanRaw({
            projectId,
            createScanRequest: {
                branch: typeof branch === "string" ? branch : "",
                revision: typeof revision === "string" && revision ? revision : undefined,
                // Without enhancers, every installed one runs; with none, none does
                enhancers: Array.isArray(enhancers) ? enhancers : undefined,
                // Without excludes, the project's own are left out; with none, no file is
                excludes: Array.isArray(excludes) ? excludes : undefined,
                // Without languages, the project's own are scanned; with none, every one found is
                languages: Array.isArray(languages) ? languages : undefined,
            },
        }),
        "create scan",
    );
}
