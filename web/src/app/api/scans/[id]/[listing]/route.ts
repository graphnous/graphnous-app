import {
    ListScanClassesDirectionEnum,
    ListScanClassesSortEnum,
    ListScanFilesDirectionEnum,
    ListScanFilesSortEnum,
    ListScanFilesSourceSetEnum,
    ListScanMethodsDirectionEnum,
    ListScanMethodsKindEnum,
    ListScanMethodsSortEnum,
    ListScanModulesDirectionEnum,
    ListScanModulesSortEnum,
    ListScanPackagesDirectionEnum,
    ListScanPackagesSortEnum,
} from "@/generated/api";
import { forwardApi, graphnousApi, queryOf } from "@/lib/server/graphnousApi";
import { NextResponse, type NextRequest } from "next/server";

/**
 * A listing of what a scan found: its modules, packages, files, classes or
 * methods, a page at a time, with the filters and sorts of each.
 */
export async function GET(
    request: NextRequest,
    { params }: { params: Promise<{ id: string; listing: string }> },
) {
    const { id: scanId, listing } = await params;
    const query = queryOf(request);
    const code = graphnousApi().scanCode;

    const paging = {
        query: query.string("query"),
        page: query.integer("page"),
        size: query.integer("size"),
    };

    switch (listing) {
        case "modules":
            return forwardApi(() => code.listScanModulesRaw({
                scanId,
                ...paging,
                target: query.string("target"),
                sort: query.oneOf("sort", ListScanModulesSortEnum),
                direction: query.oneOf("direction", ListScanModulesDirectionEnum),
            }), "get scan modules");
        case "packages":
            return forwardApi(() => code.listScanPackagesRaw({
                scanId,
                ...paging,
                module: query.string("module"),
                sort: query.oneOf("sort", ListScanPackagesSortEnum),
                direction: query.oneOf("direction", ListScanPackagesDirectionEnum),
            }), "get scan packages");
        case "files":
            return forwardApi(() => code.listScanFilesRaw({
                scanId,
                ...paging,
                module: query.string("module"),
                language: query.string("language"),
                sourceSet: query.oneOf("sourceSet", ListScanFilesSourceSetEnum),
                sort: query.oneOf("sort", ListScanFilesSortEnum),
                direction: query.oneOf("direction", ListScanFilesDirectionEnum),
            }), "get scan files");
        case "classes":
            return forwardApi(() => code.listScanClassesRaw({
                scanId,
                ...paging,
                module: query.string("module"),
                kind: query.string("kind"),
                sort: query.oneOf("sort", ListScanClassesSortEnum),
                direction: query.oneOf("direction", ListScanClassesDirectionEnum),
            }), "get scan classes");
        case "methods":
            return forwardApi(() => code.listScanMethodsRaw({
                scanId,
                ...paging,
                module: query.string("module"),
                kind: query.oneOf("kind", ListScanMethodsKindEnum),
                className: query.string("className"),
                sort: query.oneOf("sort", ListScanMethodsSortEnum),
                direction: query.oneOf("direction", ListScanMethodsDirectionEnum),
            }), "get scan methods");
        default:
            return NextResponse.json({ code: "NOT_FOUND", message: "No such listing of a scan." }, { status: 404 });
    }
}
