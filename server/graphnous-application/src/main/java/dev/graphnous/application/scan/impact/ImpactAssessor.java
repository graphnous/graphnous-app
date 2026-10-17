package dev.graphnous.application.scan.impact;

import dev.graphnous.application.context.RequestContext;

/**
 * Judges an impact report: ranks what it risks breaking and says why, in
 * Markdown, such as an AI model does.
 */
public interface ImpactAssessor {

    /**
     * @throws IllegalStateException when it cannot, such as without a model
     *                               to run on
     */
    String assess(RequestContext context, ImpactReport report);
}
