import { ListSystemDependenciesDirectionEnum, ListSystemDependenciesSortEnum } from "@/generated/api";
import { forwardApi, graphnousApi, queryOf } from "@/lib/server/graphnousApi";
import { type NextRequest } from "next/server";

/**
 * The libraries the system's projects depend on, each with its module and
 * project, a page at a time:
 * ?query=...&module=...&scope=...&page=0&size=50&sort=name&direction=asc,
 * each optional.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
    const { id: systemId } = await params;
    const query = queryOf(request);

    return forwardApi(
        () => graphnousApi().systems.listSystemDependenciesRaw({
            systemId,
            query: query.string("query"),
            module: query.string("module"),
            scope: query.string("scope"),
            page: query.integer("page"),
            size: query.integer("size"),
            sort: query.oneOf("sort", ListSystemDependenciesSortEnum),
            direction: query.oneOf("direction", ListSystemDependenciesDirectionEnum),
        }),
        "get system dependencies",
    );
}
