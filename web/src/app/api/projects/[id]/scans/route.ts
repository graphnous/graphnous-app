import type { CreateScanRequest } from "@/generated/api";
import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { NextResponse, type NextRequest } from "next/server";

const PARAMETERS = ["page", "size", "sort", "direction"];

/**
 * A page of the project's scans: ?page, size, sort (createdAt or
 * updatedAt) and direction, each optional. The API answers 400 for a sort
 * or size it does not take.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: projectId } = await params;

    const query = new URLSearchParams();

    for (const name of PARAMETERS) {
        const value = request.nextUrl.searchParams.get(name);

        if (value) {
            query.set(name, value);
        }
    }

    const search = query.size > 0 ? `?${query}` : "";

    return forward(
        () => graphnousFetch(
            `/api/v1/projects/${encodeURIComponent(projectId)}/scans${search}`,
            request.headers.get("authorization"),
        ),
        "list scans",
    );
}


/**
 * Creates a system from { name, description? }; 201 with the new system.
 */
export async function POST(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    let body: unknown;
    const { id: projectId } = await params;

    try {
        body = await request.json();
    } catch {
        return NextResponse.json({ code: "MALFORMED_REQUEST", message: "The body must be JSON." }, { status: 400 });
    }

    const { branch, revision, enhancers, excludes } = (typeof body === "object" && body !== null ? body : {}) as Partial<
        Record<keyof CreateScanRequest | "enhancers" | "excludes", unknown>
    >;

    // Not through the generated client: it does not know the enhancers and
    // the files to leave out yet
    return forward(
        () => graphnousFetch(
            `/api/v1/projects/${encodeURIComponent(projectId)}/scans`,
            request.headers.get("authorization"),
            {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                // Without enhancers, every installed one runs; with none, none does
                // Without excludes, the project's own are left out; with none, no file is
                body: JSON.stringify({
                    branch,
                    revision,
                    enhancers: Array.isArray(enhancers) ? enhancers : undefined,
                    excludes: Array.isArray(excludes) ? excludes : undefined,
                }),
            },
        ),
        "create scan",
    );
}
