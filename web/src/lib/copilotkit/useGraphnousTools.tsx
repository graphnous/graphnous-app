"use client";

import { useDefaultRenderTool, useFrontendTool } from "@copilotkit/react-core/v2";
import { Link, Spinner } from "@graphnous/theme";
import { ArrowSquareOutIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { z } from "zod";

import { ChatToolCall } from "@/components/ChatToolCall/ChatToolCall";

const openPageParameters = z.object({
    page: z
        .enum(["project", "scan", "projects", "scans", "dependencies", "graph"])
        .describe("The page to open: a project or scan of the selected system, or one of its overviews"),
    projectId: z
        .string()
        .optional()
        .describe("The id of the project, for the project and scan pages"),
    scanId: z
        .string()
        .optional()
        .describe("The id of the scan, for the scan page"),
});

type OpenPageParameters = z.infer<typeof openPageParameters>;

/**
 * The path of the page, or why there is none.
 */
function pagePath({ page, projectId, scanId }: Partial<OpenPageParameters>): string {
    switch (page) {
        case "project":
            if (!projectId) {
                throw new Error("The project page needs a projectId");
            }

            return `/projects/${encodeURIComponent(projectId)}`;
        case "scan":
            if (!projectId || !scanId) {
                throw new Error("The scan page needs a projectId and a scanId");
            }

            return `/projects/${encodeURIComponent(projectId)}/scans/${encodeURIComponent(scanId)}`;
        case "projects":
        case "scans":
        case "dependencies":
        case "graph":
            return `/${page}`;
        default:
            throw new Error(`There is no page ${page}`);
    }
}

/**
 * What the assistant can do in the browser, and how its tool calls show in
 * the chat: the server's tools with views of what they found, see
 * ChatToolCall.
 */
export function useGraphnousTools() {
    const router = useRouter();

    useDefaultRenderTool({
        render: ({ name, parameters, status, result }) => (
            <ChatToolCall name={name} parameters={parameters} status={status} result={result} />
        ),
    });

    useFrontendTool<OpenPageParameters>(
        {
            name: "openPage",
            description:
                "Opens a page of Graphnous for the user, leaving the chat: a project, a scan with its steps and logs, " +
                "or the projects, scans, dependencies or graph overview of the selected system. Only when the user " +
                "asks to go to or open a page; to point them to one, answer instead.",
            parameters: openPageParameters,
            // The chat is left behind, so there is nothing to follow up on
            followUp: false,
            handler: async (parameters) => {
                const path = pagePath(parameters);

                router.push(path);

                return `Opened ${path}`;
            },
            render: ({ args, status }) => {
                if (status !== "complete") {
                    return (
                        <div className="my-2 flex items-center gap-2 text-sm text-foreground-muted">
                            <Spinner size="sm" label="Opening the page" />
                            <span>Opening the page…</span>
                        </div>
                    );
                }

                let path: string;

                try {
                    path = pagePath(args);
                } catch {
                    return null;
                }

                // Also shown for the conversation later, to go back there
                return (
                    <div className="my-2 flex items-center gap-2 text-sm">
                        <ArrowSquareOutIcon className="size-4 text-foreground-muted" aria-hidden />
                        <Link href={path}>Open the {args.page} page</Link>
                    </div>
                );
            },
        },
        [router],
    );
}
