import { ListScanDependenciesDirectionEnum, ListScanDependenciesSortEnum } from "@/generated/api";
import { forwardApi, graphnousApi, queryOf } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * The libraries the scan's modules depend on, a page at a time:
 * ?query=...&module=...&scope=...&page=0&size=50&sort=name&direction=asc,
 * each optional.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: scanId } = await params;
    const query = queryOf(request);

    return forwardApi(
        () => graphnousApi().scanCode.listScanDependenciesRaw({
            scanId,
            query: query.string("query"),
            module: query.string("module"),
            scope: query.string("scope"),
            page: query.integer("page"),
            size: query.integer("size"),
            sort: query.oneOf("sort", ListScanDependenciesSortEnum),
            direction: query.oneOf("direction", ListScanDependenciesDirectionEnum),
        }),
        "get scan dependencies",
    );
}
