package dev.graphnous.api.ai;

import dev.graphnous.application.context.RequestContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The impact agent, as a tool of the chat assistant, for the user it runs
 * for.
 */
class ImpactTools {

    private final ImpactAgent agent;
    private final RequestContext context;

    ImpactTools(final ImpactAgent agent, final RequestContext context) {
        this.agent = agent;
        this.context = context;
    }

    @Tool(
        name = "assessImpact",
        description = "Assesses what a change of a project risks breaking, from one of its scans (base, e.g. the "
            + "newest COMPLETED scan of main) to another (head, e.g. of a branch), from listScans: an agent of its "
            + "own reads the impact report, looks closer at the highest risks, and answers with the overall risk "
            + "and the risks ranked, in Markdown, with where each is. Takes a while; use it when the user asks "
            + "what a branch or change affects, risks or breaks, and give them its answer."
    )
    String assessImpact(
        @ToolParam(description = "The id of the scan to compare from, e.g. of main, from listScans") final String baseScanId,
        @ToolParam(description = "The id of the scan to compare to, e.g. of the branch, from listScans") final String headScanId
    ) {
        return agent.assess(context, baseScanId, headScanId, null);
    }
}
