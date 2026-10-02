"use client";

import { useCallback, useEffect, useEffectEvent, useRef, useState } from "react";

export type PollingOptions<T> = {
  /**
   * Milliseconds between the end of one request and the start of the next.
   */
  interval?: number;
  /**
   * Whether it is finished, such as a scan that completed or failed; then
   * it stops polling.
   */
  until: (data: T) => boolean;
  /**
   * Stops polling, such as before an id is known.
   */
  enabled?: boolean;
};

export type Polling<T> = {
  /**
   * The latest data; kept when a later request fails.
   */
  data: T | undefined;
  /**
   * The error of the latest request, if it failed; polling goes on.
   */
  error: unknown;
  /**
   * Whether it is still polling.
   */
  polling: boolean;
  /**
   * Fetches now, and polls again if it was finished, such as after
   * starting a new scan.
   */
  refresh: () => void;
};

/**
 * Fetches something again and again while it is in progress, such as a
 * scan and its steps, and stops once it is finished. One request at a
 * time; it waits while the page is in the background and fetches right
 * away when it comes back.
 */
export function usePolling<T>(
  fetcher: (signal: AbortSignal) => Promise<T>,
  { interval = 2000, until, enabled = true }: PollingOptions<T>,
): Polling<T> {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<unknown>();
  const [finished, setFinished] = useState(false);
  const [run, setRun] = useState(0);
  const wake = useRef<() => void>(() => {});

  const fetchOnce = useEffectEvent((signal: AbortSignal) => fetcher(signal));
  const isFinished = useEffectEvent((value: T) => until(value));

  useEffect(() => {
    if (!enabled || finished) {
      return;
    }

    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;

    const poll = async () => {
      timer = undefined;

      if (document.hidden) {
        // Polls again when the page is visible
        wake.current = poll;
        return;
      }
      wake.current = () => {};

      try {
        const value = await fetchOnce(controller.signal);
        if (controller.signal.aborted) {
          return;
        }
        setData(value);
        setError(undefined);
        if (isFinished(value)) {
          setFinished(true);
          return;
        }
      } catch (caught) {
        if (controller.signal.aborted) {
          return;
        }
        setError(caught);
      }

      timer = setTimeout(poll, interval);
    };

    const onVisibilityChange = () => {
      if (!document.hidden) {
        wake.current();
      }
    };

    document.addEventListener("visibilitychange", onVisibilityChange);
    poll();

    return () => {
      controller.abort();
      clearTimeout(timer);
      wake.current = () => {};
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [enabled, finished, interval, run]);

  const refresh = useCallback(() => {
    setFinished(false);
    setRun((count) => count + 1);
  }, []);

  return { data, error, polling: enabled && !finished, refresh };
}
