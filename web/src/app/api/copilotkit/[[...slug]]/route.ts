import { HttpAgent } from "@ag-ui/client";
import {
  CopilotRuntime,
  createCopilotEndpoint,
} from "@copilotkit/runtime/v2";
import { handle } from "hono/vercel";

import { GraphnousAgentRunner } from "@/lib/copilotkit/GraphnousAgentRunner";
import { graphnousApiUrl } from "@/lib/server/graphnousApi";

/**
 * The assistant of graphnous-server, which serves it over AG-UI at /agent.
 */
const agentUrl = `${graphnousApiUrl}/agent`;

const runtime = new CopilotRuntime({
  // An agent for each request, that calls the server as the caller: their
  // access token, when they sent one, goes along, as the server checks it
  // when security is on and looks up only what the caller may see
  agents: ({ request }) => {
    const authorization = request.headers.get("authorization");

    return {
      default: new HttpAgent({
        url: agentUrl,
        headers: authorization ? { Authorization: authorization } : {},
      }),
    };
  },
  // The server keeps each conversation in its chat thread; the runner brings
  // back the ones it does not have in memory
  runner: new GraphnousAgentRunner(),
});

const app = createCopilotEndpoint({
  runtime,
  basePath: "/api/copilotkit",
});

export const GET = handle(app);
export const POST = handle(app);
export const PATCH = handle(app);
export const DELETE = handle(app);
