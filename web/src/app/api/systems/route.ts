import {
  Configuration,
  FetchError,
  ListSystemsDirectionEnum,
  ListSystemsSortEnum,
  ResponseError,
  SystemsApi,
  type CreateSystemRequest,
} from "@/generated/api";
import { NextResponse, type NextRequest } from "next/server";
import { graphnousApiUrl } from "@/lib/server/graphnousApi";

/**
 * The API of graphnous-server, as graphnousApiUrl says.
 */
const basePath = graphnousApiUrl;

/**
 * A client of the API.
 */
function systemsApi(): SystemsApi {
  return new SystemsApi(
    new Configuration({
      basePath,
      fetchApi: fetch
    }),
  );
}

/**
 * Passes the API's error on as it is, status and body, so callers see why
 * (such as a name already taken); 502 when the API could not be reached.
 */
async function errorResponse(error: unknown, action: string): Promise<NextResponse> {
  if (error instanceof ResponseError) {
    const body = await error.response.text();

    return new NextResponse(body || null, {
      status: error.response.status,
      headers: { "Content-Type": error.response.headers.get("Content-Type") ?? "application/json" },
    });
  }

  console.error(`Failed to ${action}:`, error);

  return error instanceof FetchError
    ? NextResponse.json({ code: "NETWORK_ERROR", message: "The Graphnous API could not be reached." }, { status: 502 })
    : NextResponse.json({ code: "INTERNAL_ERROR", message: `Failed to ${action}.` }, { status: 500 });
}

function badRequest(message: string): NextResponse {
  return NextResponse.json({ code: "VALIDATION_ERROR", message }, { status: 400 });
}

/**
 * The value when it is one of the allowed ones.
 */
function oneOf<T extends string>(allowed: Record<string, T>, value: string): T | undefined {
  return Object.values(allowed).find((option) => option === value);
}

/**
 * Lists systems, a page at a time: ?page=0&size=20&sort=name&direction=asc,
 * each optional.
 */
export async function GET(request: NextRequest) {
  const params = request.nextUrl.searchParams;
  const page = params.get("page");
  const size = params.get("size");
  const sort = params.get("sort");
  const direction = params.get("direction");

  if (page !== null && !/^\d+$/.test(page)) {
    return badRequest("page must be a whole number, 0 or more.");
  }
  if (size !== null && !/^[1-9]\d*$/.test(size)) {
    return badRequest("size must be a whole number, 1 or more.");
  }
  if (sort !== null && !oneOf(ListSystemsSortEnum, sort)) {
    return badRequest(`sort must be one of: ${Object.values(ListSystemsSortEnum).join(", ")}.`);
  }
  if (direction !== null && !oneOf(ListSystemsDirectionEnum, direction)) {
    return badRequest(`direction must be one of: ${Object.values(ListSystemsDirectionEnum).join(", ")}.`);
  }

  try {
    const systems = await systemsApi().listSystems({
      page: page === null ? undefined : Number(page),
      size: size === null ? undefined : Number(size),
      sort: sort === null ? undefined : oneOf(ListSystemsSortEnum, sort),
      direction: direction === null ? undefined : oneOf(ListSystemsDirectionEnum, direction),
    });

    return NextResponse.json(systems);
  } catch (error) {
    return errorResponse(error, "list systems");
  }
}

/**
 * Creates a system from { name, description? }; 201 with the new system.
 */
export async function POST(request: NextRequest) {
  let body: unknown;

  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ code: "MALFORMED_REQUEST", message: "The body must be JSON." }, { status: 400 });
  }

  const { name, description } = (typeof body === "object" && body !== null ? body : {}) as Partial<
    Record<keyof CreateSystemRequest, unknown>
  >;

  if (typeof name !== "string" || name.trim() === "") {
    return badRequest("name is required.");
  }
  if (description != null && typeof description !== "string") {
    return badRequest("description must be text.");
  }

  try {
    const system = await systemsApi().createSystem({
      createSystemRequest: { name: name.trim(), description: description ?? undefined },
    });

    return NextResponse.json(system, { status: 201 });
  } catch (error) {
    return errorResponse(error, "create system");
  }
}
