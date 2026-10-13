import { forward, graphnousFetch } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * What the project's scans leave out: { excludes }, the patterns of the
 * files, such as *.spec.ts.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id } = await params;

    return forward(
        () => graphnousFetch(
            `/api/v1/projects/${encodeURIComponent(id)}/scan-settings`,
            request.headers.get("authorization"),
        ),
        "get the project's scan settings",
    );
}

/**
 * Sets what the project's scans leave out from now on, from { excludes };
 * 400 for a pattern that is not well formed.
 */
export async function PUT(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id } = await params;
    const body = await request.text();

    return forward(
        () => graphnousFetch(
            `/api/v1/projects/${encodeURIComponent(id)}/scan-settings`,
            request.headers.get("authorization"),
            { method: "PUT", headers: { "Content-Type": "application/json" }, body },
        ),
        "update the project's scan settings",
    );
}
