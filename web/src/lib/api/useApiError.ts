"use client";

import { useEffect, useState } from "react";

import { readApiError, type ApiProblem } from "./errors";

/**
 * What went wrong with a request, read from the error the API client threw;
 * null without an error, and while it is being read.
 */
export function useApiError(error: unknown): ApiProblem | null {
  const [read, setRead] = useState<{ error: unknown; problem: ApiProblem } | null>(null);

  useEffect(() => {
    if (!error) {
      return;
    }

    let current = true;
    readApiError(error).then((problem) => {
      if (current) {
        setRead({ error, problem });
      }
    });

    return () => {
      current = false;
    };
  }, [error]);

  // A problem read for an earlier error does not apply to this one
  return error && read?.error === error ? read.problem : null;
}
