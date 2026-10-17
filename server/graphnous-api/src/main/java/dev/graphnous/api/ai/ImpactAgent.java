package dev.graphnous.api.ai;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.scan.impact.ImpactAssessor;
import dev.graphnous.application.scan.impact.ImpactReport;
import dev.graphnous.mcp.GraphnousTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.Set;

/**
 * Judges what a change of a project risks breaking: the impact report,
 * which code finds the same every time, given to a model of its own to
 * rank and explain, with a few tools to look closer at what matters. It
 * runs for the chat assistant, as a tool, so the report and the lookups
 * stay out of the conversation, which gets the assessment only.
 */
public class ImpactAgent implements ImpactAssessor {

    static final String SYSTEM_PROMPT = """
        You assess what a change of a software project risks breaking, for
        a developer about to merge it. You get an impact report, made by
        comparing a scan of the project before the change (base, such as
        main) with one after it (head, such as a branch): what the head
        removes or changes, what uses that, the HTTP calls of the system
        that no longer reach an endpoint, and the projects affected.

        Rank what the report shows by how likely it breaks something:
        - Broken calls first: they fail at run time, often in another
          project, where no compiler catches them.
        - Then removed or changed classes, methods and fields that other
          projects use, then those only this project uses: a build may
          catch these, but not over HTTP or in untyped code.
        - Removed endpoints nothing calls, and changes nothing uses, are
          low risk; say so briefly rather than listing them.
        A renamed method or field, and a method whose signature changed,
        say what replaced them; their usages are of the old one, which
        still need to change with it. Places in tests are counted apart:
        what only tests use is lower risk. Endpoints the change leaves
        without callers break nothing, but may be dead code to remove;
        mention them briefly.

        Look closer, with the tools, at no more than a handful of the
        highest risks, such as to see how a place uses what changed; do
        not look up what the report already says.

        Answer in Markdown: first a line with the overall risk, low, medium
        or high, and why in a sentence; then the risks, most likely to
        break first, each with what changed, what it breaks, and where, as
        project, file and line; then, in a line, what looks safe. Only say
        what the report or the tools show, and name what you could not
        check. Be brief.
        """;

    /**
     * The tools the agent may look closer with: lookups, not the report
     * again.
     */
    static final Set<String> TOOLS = Set.of("findUsages", "findFile", "getClass", "findMethods", "listEndpoints");

    /**
     * The most usages of each change the report gives the model; it can
     * find the rest with findUsages.
     */
    static final int REPORT_USAGES = 5;

    private final ChatModels chatModels;
    private final GraphnousTools tools;
    private final JsonMapper json;
    private final String model;

    public ImpactAgent(
        final ChatModels chatModels,
        final GraphnousTools tools,
        final JsonMapper json,
        final String model
    ) {
        this.chatModels = chatModels;
        this.tools = tools;
        this.json = json;
        this.model = model;
    }

    /**
     * The assessment, in Markdown, of what the head scan of a project risks
     * breaking compared with the base scan, for the user of the context.
     *
     * @throws IllegalStateException when there is no model to run on
     */
    public String assess(
        final RequestContext context,
        final String baseScanId,
        final String headScanId,
        final Integer depth
    ) {
        final var scoped = tools.forContext(context);

        return assess(scoped, scoped.analyzeImpact(baseScanId, headScanId, depth));
    }

    /**
     * The assessment, in Markdown, of a report already made, for the user
     * of the context.
     *
     * @throws IllegalStateException when there is no model to run on
     */
    @Override
    public String assess(final RequestContext context, final ImpactReport report) {
        final var scoped = tools.forContext(context);

        return assess(scoped, scoped.impactSummary(report));
    }

    private String assess(final GraphnousTools scoped, final GraphnousTools.ImpactSummary summary) {
        final var report = compact(summary);

        final var callbacks = Arrays.stream(MethodToolCallbackProvider.builder().toolObjects(scoped).build().getToolCallbacks())
            .filter(callback -> TOOLS.contains(callback.getToolDefinition().name()))
            .toArray(ToolCallback[]::new);

        // A message of its own, as the report's braces are not a template's
        return ChatClient.builder(chatModels.get(model))
            .build()
            .prompt()
            .system(SYSTEM_PROMPT)
            .messages(new UserMessage("Assess this impact report:\n\n" + json.writeValueAsString(report)))
            .toolCallbacks(callbacks)
            .call()
            .content();
    }

    /**
     * The report with the first few usages of each change, which is what
     * the model needs to rank them.
     */
    static GraphnousTools.ImpactSummary compact(final GraphnousTools.ImpactSummary report) {
        return new GraphnousTools.ImpactSummary(
            report.base(),
            report.head(),
            report.summary(),
            report.changes()
                .stream()
                .map(change -> new GraphnousTools.ImpactChange(
                    change.kind(),
                    change.id(),
                    change.type(),
                    change.name(),
                    change.properties(),
                    change.replacement(),
                    change.usageCount(),
                    change.testUsageCount(),
                    change.truncated() || change.usages().size() > REPORT_USAGES,
                    change.usages().stream().limit(REPORT_USAGES).toList()
                ))
                .toList(),
            report.removedEndpoints(),
            report.addedEndpoints(),
            report.movedEndpoints(),
            report.unusedEndpoints(),
            report.brokenCalls(),
            report.projects(),
            report.truncated()
        );
    }
}
