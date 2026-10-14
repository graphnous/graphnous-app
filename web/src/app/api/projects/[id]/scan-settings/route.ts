import { forwardApi, graphnousApi } from "@/lib/server/graphnousApi";
import { NextResponse, type NextRequest } from "next/server";

/**
 * What the project's scans leave out: { excludes }, the patterns of the
 * files, such as *.spec.ts; and { languages }, the ones they scan, such as
 * JAVA, none for every language they find.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: projectId } = await params;

    return forwardApi(
        () => graphnousApi().projects.getProjectScanSettingsRaw({ projectId }),
        "get the project's scan settings",
    );
}

/**
 * Sets what the project's scans leave out and which languages they scan
 * from now on, from { excludes, languages }; one left out stays as it is.
 * 400 for a pattern that is not well formed, or a language the server has
 * no scanner for.
 */
export async function PUT(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: projectId } = await params;
    const body = await jsonBody(request);

    if (body === undefined) {
        return NextResponse.json({ code: "MALFORMED_REQUEST", message: "The body must be JSON." }, { status: 400 });
    }

    const { excludes, languages } = body;

    return forwardApi(
        () => graphnousApi().projects.updateProjectScanSettingsRaw({
            projectId,
            // A setting left out stays as it is
            scanSettings: {
                excludes: Array.isArray(excludes) ? excludes : undefined,
                languages: Array.isArray(languages) ? languages : undefined,
            },
        }),
        "update the project's scan settings",
    );
}

async function jsonBody(request: NextRequest): Promise<Record<string, unknown> | undefined> {
    try {
        const body: unknown = await request.json();

        return typeof body === "object" && body !== null ? (body as Record<string, unknown>) : {};
    } catch {
        return undefined;
    }
}
