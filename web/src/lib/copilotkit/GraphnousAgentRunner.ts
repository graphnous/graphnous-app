import { EventType, type BaseEvent, type Message } from "@ag-ui/client";
import {
    InMemoryAgentRunner,
    type AgentRunnerConnectRequest,
} from "@copilotkit/runtime/v2";
import { Observable } from "rxjs";

import { graphnousFetch } from "@/lib/server/graphnousApi";

/**
 * Runs the assistant as the in-memory runner does, and brings back
 * conversations it does not know, such as after a restart, from the
 * Graphnous server, which keeps each one in its chat thread: connecting to
 * such a thread replays its messages as a snapshot.
 */
export class GraphnousAgentRunner extends InMemoryAgentRunner {

    connect(request: AgentRunnerConnectRequest): Observable<BaseEvent> {
        if (this.getThreadMessages(request.threadId).length > 0) {
            return super.connect(request);
        }

        return new Observable<BaseEvent>((subscriber) => {
            const authorization = request.headers?.authorization ?? request.headers?.Authorization;

            savedMessages(request.threadId, authorization)
                .then((messages) => {
                    if (messages.length > 0) {
                        const runId = `restore-${request.threadId}`;

                        // A run of its own, as clients take events in a run only
                        subscriber.next({ type: EventType.RUN_STARTED, threadId: request.threadId, runId } as BaseEvent);
                        subscriber.next({ type: EventType.MESSAGES_SNAPSHOT, messages } as BaseEvent);
                        subscriber.next({ type: EventType.RUN_FINISHED, threadId: request.threadId, runId } as BaseEvent);
                    }

                    subscriber.complete();
                })
                .catch((error) => {
                    // An empty conversation rather than a broken chat
                    console.error(`Failed to restore conversation ${request.threadId}:`, error);
                    subscriber.complete();
                });
        });
    }
}

/**
 * The messages the server kept of the thread; none for a thread it does not
 * have, or that is not the caller's.
 */
async function savedMessages(threadId: string, authorization: string | undefined): Promise<Message[]> {
    const response = await graphnousFetch(`/api/v1/chat/threads/${encodeURIComponent(threadId)}`, authorization);

    if (!response.ok) {
        return [];
    }

    const thread: { messages?: Message[] } = await response.json();

    return Array.isArray(thread.messages) ? thread.messages : [];
}
