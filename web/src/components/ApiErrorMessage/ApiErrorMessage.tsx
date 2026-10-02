"use client";

import { ArrowClockwiseIcon, ArrowLeftIcon } from "@phosphor-icons/react/ssr";

import { Alert, Button, ButtonLink } from "graphnous-theme";

import { describeApiError, type ApiProblem } from "@/lib/api/errors";
import { useApiError } from "@/lib/api/useApiError";

export type ApiErrorMessageProps = {
  /**
   * The error the API client threw, or what was read from it.
   */
  error: unknown;
  /**
   * Shows "Try again" for errors that may not happen again, such as the
   * server being unreachable.
   */
  onRetry?: () => void;
  /**
   * Where to go back to when something is not found, such as the projects.
   */
  backHref?: string;
  backLabel?: string;
  /**
   * Announces it to screen readers when it appears, after something the
   * user did.
   */
  announce?: boolean;
  className?: string;
};

function isProblem(error: unknown): error is ApiProblem {
  return typeof error === "object" && error !== null && "code" in error && !(error instanceof Error);
}

/**
 * What went wrong with a request, in words: the API's message where it
 * helps, and a way forward, such as trying again or going back.
 */
export function ApiErrorMessage({
  error,
  onRetry,
  backHref,
  backLabel = "Go back",
  announce = false,
  className,
}: ApiErrorMessageProps) {
  const read = useApiError(isProblem(error) ? null : error);
  const problem = isProblem(error) ? error : read;

  if (!problem) {
    return null;
  }

  const { tone, title, message, retry } = describeApiError(problem);
  const back = problem.code === "NOT_FOUND" && backHref;

  return (
    <Alert
      tone={tone}
      title={title}
      announce={announce}
      className={className}
      action={
        (retry && onRetry) || back ? (
          <div className="flex gap-2">
            {retry && onRetry && (
              <Button size="sm" variant="secondary" icon={ArrowClockwiseIcon} onClick={onRetry}>
                Try again
              </Button>
            )}
            {back && (
              <ButtonLink size="sm" variant="secondary" icon={ArrowLeftIcon} href={backHref}>
                {backLabel}
              </ButtonLink>
            )}
          </div>
        ) : undefined
      }
    >
      {message}
    </Alert>
  );
}
