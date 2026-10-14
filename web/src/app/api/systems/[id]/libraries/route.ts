import { ListSystemLibrariesDirectionEnum, ListSystemLibrariesSortEnum } from "@/generated/api";
import { forwardApi, graphnousApi, queryOf } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * Each library the system's projects depend on, with its versions and the
 * projects that use each, a page at a time:
 * ?query=...&drift=true&page=0&size=50&sort=name&direction=asc, each
 * optional.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: systemId } = await params;
    const query = queryOf(request);

    return forwardApi(
        () => graphnousApi().systems.listSystemLibrariesRaw({
            systemId,
            query: query.string("query"),
            drift: query.boolean("drift"),
            page: query.integer("page"),
            size: query.integer("size"),
            sort: query.oneOf("sort", ListSystemLibrariesSortEnum),
            direction: query.oneOf("direction", ListSystemLibrariesDirectionEnum),
        }),
        "get system libraries",
    );
}
