import { forwardApi, graphnousApi } from "@/lib/server/graphnousApi";
import { NextResponse, type NextRequest } from "next/server";

/**
 * Enhances the scan again, from { enhancers, restore }, each optional:
 * every installed enhancer without enhancers, and without storing the
 * results again first without restore.
 */
export async function POST(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: scanId } = await params;
    const text = await request.text();
    let body: Record<string, unknown> = {};

    if (text) {
        try {
            const parsed: unknown = JSON.parse(text);
            body = typeof parsed === "object" && parsed !== null ? (parsed as Record<string, unknown>) : {};
        } catch {
            return NextResponse.json({ code: "MALFORMED_REQUEST", message: "The body must be JSON." }, { status: 400 });
        }
    }

    const { enhancers, restore } = body;

    return forwardApi(
        () => graphnousApi().scans.enhanceScanRaw({
            scanId,
            enhanceScanRequest: {
                // Without enhancers, every installed one runs; with none, none does
                enhancers: Array.isArray(enhancers) ? enhancers : undefined,
                restore: typeof restore === "boolean" ? restore : undefined,
            },
        }),
        "enhance scan",
    );
}
