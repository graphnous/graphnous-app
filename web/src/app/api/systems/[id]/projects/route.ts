import {
  BASE_PATH,
  Configuration,
  CreateProjectRequest,
  FetchError,
  ProjectsApi,
  ResponseError,
} from "@/generated/api";
import { NextResponse, type NextRequest } from "next/server";

/**
 * The API of graphnous-server: NEXT_PUBLIC_GRAPHNOUS_API_URL (such as in
 * .env.local), or else the server in the spec.
 */
const basePath = (process.env.NEXT_PUBLIC_GRAPHNOUS_API_URL ?? BASE_PATH).replace(/\/+$/, "");

/**
 * A client that calls the API as the caller: their access token, when they
 * sent one, goes along, as the server checks it when security is on.
 */
function projectsApi(request: NextRequest): ProjectsApi {
  const authorization = request.headers.get("authorization");

  return new ProjectsApi(
    new Configuration({
      basePath,
      headers: authorization ? { Authorization: authorization } : undefined,
    }),
  );
}

function badRequest(message: string): NextResponse {
  return NextResponse.json({ code: "VALIDATION_ERROR", message }, { status: 400 });
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

/**
 * Lists systems, a page at a time: ?page=0&size=20&sort=name&direction=asc,
 * each optional.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id: systemId } = await params;

  try {
    const projects = await projectsApi(request).listProjects({
      systemId
    })

    return NextResponse.json(projects);
  } catch (error) {
    return errorResponse(error, "list projects");
  }
}


/**
 * Creates a system from { name, description? }; 201 with the new system.
 */
export async function POST(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  let body: unknown;
  const { id: systemId } = await params;

  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ code: "MALFORMED_REQUEST", message: "The body must be JSON." }, { status: 400 });
  }

  const { name, description, gitUrl } = (typeof body === "object" && body !== null ? body : {}) as Partial<
    Record<keyof CreateProjectRequest, unknown>
  >;

  if (typeof name !== "string" || name.trim() === "") {
    return badRequest("name is required.");
  }
  if (description != null && typeof description !== "string") {
    return badRequest("description must be text.");
  }

  if (typeof gitUrl !== "string" || gitUrl.trim() === "") {
    return badRequest("git url is required.");
  }

  try {
    const system = await projectsApi(request).createProject({
      createProjectRequest: { name: name.trim(), description: description ?? undefined, gitUrl: gitUrl.trim() },
      systemId
    });

    return NextResponse.json(system, { status: 201 });
  } catch (error) {
    return errorResponse(error, "create project");
  }
}
