import { FetchError, ResponseError } from "@/generated/api";

/**
 * The codes of the API's errors, and NETWORK_ERROR for a request that did
 * not reach it.
 */
export type ApiErrorCode =
  | "VALIDATION_ERROR"
  | "MALFORMED_REQUEST"
  | "FORBIDDEN"
  | "PLAN_LIMIT"
  | "NOT_FOUND"
  | "CONFLICT"
  | "INTERNAL_ERROR"
  | "NETWORK_ERROR";

/**
 * What went wrong with a request.
 */
export type ApiProblem = {
  code: ApiErrorCode;
  /**
   * The API's message, such as which field is wrong.
   */
  message: string;
  /**
   * The HTTP status; missing when the request did not reach the API.
   */
  status?: number;
};

const codes = new Set<string>([
  "VALIDATION_ERROR",
  "MALFORMED_REQUEST",
  "FORBIDDEN",
  "PLAN_LIMIT",
  "NOT_FOUND",
  "CONFLICT",
  "INTERNAL_ERROR",
]);

function codeFromStatus(status: number): ApiErrorCode {
  switch (status) {
    case 400:
      return "MALFORMED_REQUEST";
    case 403:
      return "FORBIDDEN";
    case 404:
      return "NOT_FOUND";
    case 409:
      return "CONFLICT";
    default:
      return "INTERNAL_ERROR";
  }
}

/**
 * Reads what went wrong from an error the API client threw: the API's
 * error body (code and message) when there is one, and otherwise what the
 * status or the failed request says.
 */
export async function readApiError(error: unknown): Promise<ApiProblem> {
  if (error instanceof ResponseError) {
    const { status } = error.response;

    try {
      const body: unknown = await error.response.clone().json();

      if (body && typeof body === "object" && "code" in body && "message" in body) {
        const { code, message } = body as { code: unknown; message: unknown };

        return {
          code: typeof code === "string" && codes.has(code) ? (code as ApiErrorCode) : codeFromStatus(status),
          message: typeof message === "string" ? message : "",
          status,
        };
      }
    } catch {
      // Not JSON, such as a proxy's error page
    }

    return { code: codeFromStatus(status), message: "", status };
  }

  if (error instanceof FetchError || error instanceof TypeError) {
    return { code: "NETWORK_ERROR", message: "" };
  }

  return { code: "INTERNAL_ERROR", message: error instanceof Error ? error.message : "" };
}

export type ApiErrorDescription = {
  tone: "warning" | "error";
  title: string;
  /**
   * The API's message, or what to do about it.
   */
  message: string;
  /**
   * Whether trying again may work.
   */
  retry: boolean;
};

/**
 * How to tell the reader what went wrong.
 */
export function describeApiError(problem: ApiProblem): ApiErrorDescription {
  const message = problem.message;

  switch (problem.code) {
    case "VALIDATION_ERROR":
    case "MALFORMED_REQUEST":
      return { tone: "error", title: "Check what you entered", message: message || "The request is not valid.", retry: false };
    case "FORBIDDEN":
      return {
        tone: "error",
        title: "You don't have permission to do this",
        message: message || "Ask an owner of the organization for access.",
        retry: false,
      };
    case "PLAN_LIMIT":
      return {
        tone: "warning",
        title: "Your plan doesn't allow this",
        message: `${message ? `${message} ` : ""}Upgrade your plan to continue.`,
        retry: false,
      };
    case "NOT_FOUND":
      return {
        tone: "warning",
        title: "Not found",
        message: message || "It may have been deleted, or the link is wrong.",
        retry: false,
      };
    case "CONFLICT":
      return { tone: "warning", title: "This can't be done right now", message: message || "Try again later.", retry: false };
    case "NETWORK_ERROR":
      return {
        tone: "error",
        title: "Couldn't reach the server",
        message: "Check your connection and try again.",
        retry: true,
      };
    default:
      return {
        tone: "error",
        title: "Something went wrong on our side",
        message: "Try again in a moment.",
        retry: true,
      };
  }
}
