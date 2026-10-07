"use client";

import {
    Alert,
    Button,
    Combobox,
    ConfirmDialog,
    Container,
    Field,
    formatRelative,
    IconButton,
    PageHeader,
    Stack,
    Text,
    useToast,
} from "@graphnous/theme";
import {
    CopilotChat,
    CopilotKit,
    useAgent,
    useAgentContext,
    UseAgentUpdate,
} from "@copilotkit/react-core/v2";
import "@copilotkit/react-core/v2/styles.css";
import { PlusIcon, TrashIcon } from "@phosphor-icons/react";
import { useEffect, useRef, useState } from "react";

import { useGraphnousTools } from "@/lib/copilotkit/useGraphnousTools";
import { useCreateChatThread } from "@/lib/hooks/chat/useCreateChatThread";
import { useDeleteChatThread } from "@/lib/hooks/chat/useDeleteChatThread";
import { useListChatThreads } from "@/lib/hooks/chat/useListChatThreads";
import { useSystemStore } from "@/lib/store/systemStore";
import type { ChatThread, System } from "@/types";

export default function ChatPage() {
    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    const { threads, loaded, error: threadsError, refresh } = useListChatThreads(selectedSystem?.id);
    const { createThread, loading: creating } = useCreateChatThread();
    const { deleteThread, loading: deleting, error: deleteError, reset } = useDeleteChatThread();
    const { toast } = useToast();

    // The conversation shown; the last one talked in, until another is chosen
    const [chosenThreadId, setChosenThreadId] = useState<string | null>(null);
    const [confirmingDelete, setConfirmingDelete] = useState(false);

    const activeThread: ChatThread | undefined =
        threads.find((thread) => thread.id === chosenThreadId) ?? threads[0];

    const startThread = async () => {
        if (!selectedSystem) {
            return;
        }

        try {
            const thread = await createThread(selectedSystem.id);

            await refresh();
            setChosenThreadId(thread.id);
        } catch {
            toast({ title: "Could not start a conversation", tone: "error" });
        }
    };

    // A system without conversations gets one, so the chat can be used
    // straight away; once per system, also when effects run twice
    const startedFor = useRef<string | null>(null);

    useEffect(() => {
        if (!selectedSystem || !loaded || threads.length > 0 || startedFor.current === selectedSystem.id) {
            return;
        }

        startedFor.current = selectedSystem.id;
        startThread();
    });

    const confirmDelete = async () => {
        if (!activeThread) {
            return;
        }

        try {
            await deleteThread(activeThread.id);
        } catch {
            // Shown in the dialog, through the hook's error
            return;
        }

        setConfirmingDelete(false);
        setChosenThreadId(null);
        reset();
        await refresh();
        toast({ title: "Conversation deleted", tone: "success" });
    };

    return (
        <Container>
            <Stack gap={8}>
                <PageHeader
                    title="System Chat"
                    breadcrumbs={[
                        { label: selectedSystem?.name || '', href: "/" },
                        { label: "Chat" },
                    ]}
                    description={`Ask questions about the architecture, code, dependencies, and relationships across ${selectedSystem?.name}.`}
                    actions={
                        <Button
                            onClick={startThread}
                            disabled={!selectedSystem || creating}
                        >
                            <PlusIcon /> New conversation
                        </Button>
                    }
                />

                {threads.length > 0 && (
                    <div className="flex items-end gap-2">
                        <div className="flex-1">
                            <Field label="Conversation">
                                <Combobox
                                    value={activeThread?.id ?? null}
                                    options={threads.map((thread) => ({
                                        value: thread.id,
                                        label: thread.title ?? "New conversation",
                                        description: formatRelative(new Date(thread.updatedAt)),
                                    }))}
                                    onValueChange={setChosenThreadId}
                                    emptyMessage="No conversations match"
                                />
                            </Field>
                        </div>

                        <IconButton
                            icon={TrashIcon}
                            label="Delete this conversation"
                            variant="ghost"
                            onClick={() => setConfirmingDelete(true)}
                        />
                    </div>
                )}

                {/* REST transport, so the runtime's info and runs both go to
                    the route's own endpoints */}
                <CopilotKit runtimeUrl="/api/copilotkit" useSingleEndpoint={false}>
                    {threadsError ? (
                        <Alert tone="error" title="The conversations could not be loaded">
                            {threadsError.message}
                        </Alert>
                    ) : activeThread ? (
                        <SystemChat
                            // A conversation of its own for each thread
                            key={activeThread.id}
                            system={selectedSystem}
                            threadId={activeThread.id}
                            onRunFinished={refresh}
                        />
                    ) : (
                        <Text>{loaded ? "Starting a conversation…" : "Loading conversations…"}</Text>
                    )}
                </CopilotKit>

                <ConfirmDialog
                    open={confirmingDelete}
                    onClose={() => {
                        setConfirmingDelete(false);
                        reset();
                    }}
                    onConfirm={confirmDelete}
                    title="Delete this conversation?"
                    description="Its messages are deleted. This cannot be undone."
                    confirmLabel="Delete conversation"
                    danger
                    confirming={deleting}
                    error={deleteError?.message}
                />
            </Stack>
        </Container>
    );
}

/**
 * The chat with the assistant in one conversation, which the server keeps.
 * The assistant is told the system the user has selected, so "my projects"
 * means that system's.
 */
function SystemChat({
    system,
    threadId,
    onRunFinished,
}: {
    system: System | null;
    threadId: string;
    /**
     * Called when the assistant has answered, such as to show the
     * conversation's new title.
     */
    onRunFinished: () => void;
}) {
    useGraphnousTools();

    useAgentContext({
        description: "The system the user has selected",
        value: system
            ? { id: system.id, name: system.name, description: system.description ?? null }
            : "None",
    });

    // The agent the chat runs, which re-renders this when a run starts or
    // ends
    const { agent } = useAgent({
        agentId: "default",
        updates: [UseAgentUpdate.OnRunStatusChanged],
    });

    const wasRunning = useRef(false);

    useEffect(() => {
        if (wasRunning.current && !agent.isRunning) {
            onRunFinished();
        }

        wasRunning.current = agent.isRunning;
    }, [agent.isRunning, onRunFinished]);

    return (
        <div className="graphnous-chat h-[70vh] overflow-hidden rounded-card border border-border">
            <CopilotChat
                agentId="default"
                threadId={threadId}
                labels={{
                    chatInputPlaceholder: system
                        ? `Ask about ${system.name}…`
                        : "Ask about your systems…",
                }}
            />
        </div>
    );
}
