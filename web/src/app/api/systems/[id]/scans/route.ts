import { ListSystemScansDirectionEnum, ListSystemScansSortEnum } from "@/generated/api";
import { forwardApi, graphnousApi, queryOf } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * The scans of every project of the system, a page at a time:
 * ?page=0&size=20&sort=createdAt&direction=desc, each optional.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: systemId } = await params;
    const query = queryOf(request);

    return forwardApi(
        () => graphnousApi().systems.listSystemScansRaw({
            systemId,
            page: query.integer("page"),
            size: query.integer("size"),
            sort: query.oneOf("sort", ListSystemScansSortEnum),
            direction: query.oneOf("direction", ListSystemScansDirectionEnum),
        }),
        "get system scans",
    );
}
