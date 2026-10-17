"use client";

import { useEffect, useEffectEvent, useState } from "react";

export type EventStreamStatus = "connecting" | "open" | "closed";

export type EventStreamOptions<T> = {
  /**
   * The name of the events, such as "scanLog", or the names of several;
   * unnamed events are "message".
   */
  event?: string | readonly string[];
  /**
   * Called with each event's data, parsed as JSON.
   */
  onEvent: (data: T) => void;
  /**
   * Whether the event stream should be active.
   */
  enabled?: boolean;
  /**
   * Sends cookies to a stream on another origin.
   */
  withCredentials?: boolean;
};

/**
 * Reads a server-sent event stream, such as a scan's live logs, from the
 * url; no url, no stream. The browser reconnects when the connection
 * drops; the stream is closed when the url changes or the component
 * unmounts.
 */
export function useEventStream<T>(
  url: string | null,
  { event = "message", onEvent, enabled = true, withCredentials = false }: EventStreamOptions<T>,
): EventStreamStatus {
  const [status, setStatus] = useState<{
    url: string;
    status: EventStreamStatus;
  } | null>(null);

  const handle = useEffectEvent((data: T) => onEvent(data));

  // A list of names as one dependency of the effect, so a new array with the same names keeps the stream
  const events = typeof event === "string" ? event : event.join(" ");

  useEffect(() => {
    if (!url || !enabled) {
      return;
    }

    const source = new EventSource(url, { withCredentials });

    const update = () =>
      setStatus({
        url,
        status:
          source.readyState === EventSource.OPEN
            ? "open"
            : source.readyState === EventSource.CONNECTING
              ? "connecting"
              : "closed",
      });

    const listener = (message: MessageEvent<string>) => {
      try {
        handle(JSON.parse(message.data) as T);
      } catch {
        // Skip an event that is not JSON
      }
    };

    const names = events.split(" ");

    source.addEventListener("open", update);
    source.addEventListener("error", update);
    names.forEach((name) => source.addEventListener(name, listener));

    update();

    return () => {
      source.removeEventListener("open", update);
      source.removeEventListener("error", update);
      names.forEach((name) => source.removeEventListener(name, listener));
      source.close();
      setStatus({ url, status: "closed" });
    };
  }, [url, events, enabled, withCredentials]);

  if (!url || !enabled) {
    return "closed";
  }

  return status?.url === url ? status.status : "connecting";
}
