package dev.graphnous.application.scan.impact;

import dev.graphnous.application.scan.links.HttpLinks;
import dev.graphnous.application.scan.usages.ScanUsages;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * An impact report as a comment of a pull request, in GitHub's Markdown:
 * the calls that no longer reach an endpoint first, then the changes that
 * something uses, with where, then the endpoints and the projects
 * affected. It is the same every time for the same report; an assessment
 * written from the report, such as by the impact agent, can head it.
 */
public final class ImpactComment {

    /**
     * Starts every comment, unseen, so a job that comments again can find
     * its last one and edit it.
     */
    public static final String MARKER = "<!-- graphnous-impact -->";

    /**
     * The most places a comment lists for each change; it counts them all.
     */
    static final int LISTED_USAGES = 10;

    /**
     * Below GitHub's limit of 65,536 characters for a comment; a longer
     * one leaves out where each change is used.
     */
    static final int MAX_LENGTH = 60_000;

    private ImpactComment() {
    }

    /**
     * @param projects   the name of each project the report names
     * @param assessment what to head the comment with, in Markdown; none
     *                   when null or blank
     */
    public static String render(
        final ImpactReport report,
        final Map<Project.ProjectId, String> projects,
        final String assessment
    ) {
        final var comment = render(report, projects, assessment, true);

        return comment.length() <= MAX_LENGTH ? comment : render(report, projects, assessment, false);
    }

    private static String render(
        final ImpactReport report,
        final Map<Project.ProjectId, String> projects,
        final String assessment,
        final boolean usages
    ) {
        final var out = new StringBuilder();

        out.append(MARKER).append("\n");
        out.append("### Graphnous impact report\n\n");
        out.append(revision(report.head())).append(" compared with ").append(revision(report.base())).append(".\n\n");

        final var used = report.changes().stream().filter(change -> change.usageCount() > 0).toList();
        final var unused = report.changes().stream().filter(change -> change.usageCount() == 0).toList();

        if (report.changes().isEmpty() && report.brokenCalls().isEmpty() && report.endpoints().removed().isEmpty()
            && report.endpoints().added().isEmpty()) {
            out.append("No class, method, field or endpoint changed.\n");
            return out.toString();
        }

        out.append("**").append(count(report.brokenCalls().size(), "broken call", "broken calls")).append("**")
            .append(" · ")
            .append(count(used.size(), "change", "changes")).append(" in use, in ")
            .append(count(used.stream().mapToInt(ImpactReport.Change::usageCount).sum(), "place", "places"))
            .append(" · ")
            .append(count(report.projects().size(), "project", "projects")).append(" affected\n\n");

        if (assessment != null && !assessment.isBlank()) {
            out.append("#### Assessment\n\n");
            out.append(assessment.strip()).append("\n\n");
            out.append("<sub>Written by an AI model from the report below. Check it against the report.</sub>\n\n");
        }

        if (!report.brokenCalls().isEmpty()) {
            out.append("#### Calls that no longer reach an endpoint\n\n");
            out.append("These reached an endpoint in ").append(branch(report.base()))
                .append(" and reach none in ").append(branch(report.head())).append(".\n\n");
            out.append("| Call | Project | Where | Reached before |\n|---|---|---|---|\n");

            report.brokenCalls().forEach(broken -> out
                .append("| ").append(code(request(broken.call().httpMethod(), broken.call().url())))
                .append(" | ").append(cell(name(projects, broken.call().projectId())))
                .append(" | ").append(where(broken.caller()))
                .append(" | ").append(broken.endpoints().stream()
                    .map(endpoint -> code(request(endpoint.httpMethod(), endpoint.url())))
                    .collect(Collectors.joining(", ")))
                .append(" |\n"));

            out.append("\n");
        }

        if (!used.isEmpty()) {
            out.append("#### Changes in use\n\n");
            out.append("| Change | | Places | Projects |\n|---|---|---|---|\n");

            used.forEach(change -> out
                .append("| ").append(change.type()).append(" ").append(code(label(change.id(), change.name())))
                .append(" | ").append(change.kind() == ImpactReport.Kind.REMOVED ? "removed" : "changed")
                .append(" | ").append(change.usageCount()).append(change.truncated() ? "+" : "")
                .append(" | ").append(cell(usageProjects(change, projects)))
                .append(" |\n"));

            out.append("\n");

            if (usages) {
                used.forEach(change -> {
                    out.append("<details><summary>Where ").append(html(label(change.id(), change.name())))
                        .append(" is used</summary>\n\n");

                    change.usages().stream().limit(LISTED_USAGES).forEach(usage -> out
                        .append("- ").append(where(usage))
                        .append(" (").append(name(projects, project(change, usage))).append(")\n"));

                    if (change.usageCount() > LISTED_USAGES) {
                        out.append("- and ").append(change.usageCount() - LISTED_USAGES).append(" more\n");
                    }

                    out.append("\n</details>\n\n");
                });
            }
        }

        if (!report.endpoints().removed().isEmpty() || !report.endpoints().added().isEmpty()) {
            out.append("#### Endpoints\n\n");
            report.endpoints().removed().forEach(endpoint -> out.append("- Removed ").append(code(endpoint.name())).append("\n"));
            report.endpoints().added().forEach(endpoint -> out.append("- Added ").append(code(endpoint.name())).append("\n"));
            out.append("\n");
        }

        if (!report.projects().isEmpty()) {
            out.append("#### Projects affected\n\n");
            out.append("| Project | Places using the changes | Broken calls |\n|---|---|---|\n");

            report.projects().forEach(project -> out
                .append("| ").append(cell(name(projects, project.projectId())))
                .append(" | ").append(project.usages())
                .append(" | ").append(project.brokenCalls())
                .append(" |\n"));

            out.append("\n");
        }

        if (!unused.isEmpty()) {
            out.append("<details><summary>").append(count(unused.size(), "change", "changes"))
                .append(" nothing uses</summary>\n\n");
            unused.forEach(change -> out
                .append("- ").append(change.kind() == ImpactReport.Kind.REMOVED ? "Removed " : "Changed ")
                .append(change.type().toLowerCase()).append(" ").append(code(label(change.id(), change.name()))).append("\n"));
            out.append("\n</details>\n\n");
        }

        if (report.truncated()) {
            out.append("<sub>The change is larger than the report follows: it lists the first ")
                .append(ImpactAnalysisService.MAX_CHANGES)
                .append(" removed and changed classes, methods and fields.</sub>\n");
        }

        return out.toString();
    }

    /**
     * What identifies a class, method or field to a reader: its name with
     * its class's, without packages or the file a TypeScript one is in,
     * such as {@code OrderService.create(String)}; from its key, whose last
     * part is that, as {@code method:com.example.OrderService.create(java.lang.String)}.
     */
    static String label(final String id, final String name) {
        final var last = id.substring(id.lastIndexOf('|') + 1);
        final var type = last.indexOf(':');

        if (type < 0) {
            return name;
        }

        var qualified = last.substring(type + 1);
        final var file = qualified.lastIndexOf(':');

        if (file >= 0) {
            qualified = qualified.substring(file + 1);
        }

        return qualified.replaceAll("\\b[a-z_][\\w$]*\\.", "");
    }

    private static String where(final HttpLinks.Source caller) {
        if (caller == null) {
            return "";
        }

        final var name = caller.className() == null
            ? caller.name()
            : simple(caller.className()) + "." + caller.name();

        return code(name) + (caller.file() == null ? "" : " in " + code(location(caller.file(), caller.line())));
    }

    private static String where(final ScanUsages.Usage usage) {
        final var name = usage.owner() == null ? usage.user().name() : simple(usage.owner()) + "." + usage.user().name();
        final var how = switch (usage.relationship()) {
            case "CALLS" -> "calls it";
            case "INSTANTIATES" -> "creates it";
            case "READS" -> "reads it";
            case "WRITES" -> "writes it";
            case "EXTENDS" -> "extends it";
            case "IMPLEMENTS" -> "implements it";
            case "OVERRIDES" -> "overrides it";
            case "REQUESTS" -> "requests it over HTTP";
            default -> "uses it";
        };

        return code(name) + " " + how + (usage.file() == null ? "" : ", in " + code(location(usage.file(), usage.line())));
    }

    private static Project.ProjectId project(final ImpactReport.Change change, final ScanUsages.Usage usage) {
        return change.projects().get(usage.scanId() == null ? change.scanId() : usage.scanId());
    }

    private static String usageProjects(final ImpactReport.Change change, final Map<Project.ProjectId, String> projects) {
        final var names = new TreeSet<String>();

        change.usages().forEach(usage -> names.add(name(projects, project(change, usage))));

        return String.join(", ", names);
    }

    private static String name(final Map<Project.ProjectId, String> projects, final Project.ProjectId id) {
        if (id == null) {
            return "unknown project";
        }

        return Objects.requireNonNullElse(projects.get(id), id.id().toString());
    }

    private static String revision(final Scan scan) {
        final var revision = scan.revision();
        final var commit = revision.revision() == null ? null : revision.revision().substring(0, Math.min(7, revision.revision().length()));

        return branch(scan) + (commit == null ? "" : " at " + code(commit));
    }

    private static String branch(final Scan scan) {
        return scan.revision().branch() == null ? "the scan" : code(scan.revision().branch());
    }

    private static String request(final String method, final String url) {
        return (method == null ? "" : method + " ") + (url == null ? "?" : url);
    }

    private static String location(final String file, final Integer line) {
        return line == null ? file : file + ":" + line;
    }

    private static String simple(final String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static String count(final int count, final String one, final String many) {
        return count + " " + (count == 1 ? one : many);
    }

    /**
     * As code, which keeps braces and underscores as written; a table
     * keeps it in its cell.
     */
    private static String code(final String text) {
        return "`" + cell(text).replace("`", "'") + "`";
    }

    private static String cell(final String text) {
        return text == null ? "" : text.replace("|", "\\|").replace("\n", " ");
    }

    private static String html(final String text) {
        return "<code>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</code>";
    }
}
