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
  // An agent for each request, that calls the server's assistant
  agents: () => ({
    default: new HttpAgent({
      url: agentUrl,
    }),
  }),
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
