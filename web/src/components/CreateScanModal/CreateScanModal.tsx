"use client";

import {
    forwardRef,
    useImperativeHandle,
    useState,
} from "react";

import {
    Button,
    Checkbox,
    Dialog,
    Field,
    Text,
    TextArea,
    TextInput,
} from "@graphnous/theme";

import { useProjectScanSettings } from "@/lib/hooks/project/useProjectScanSettings";
import { useListEnhancers } from "@/lib/hooks/enhancer/useListEnhancers";

import { useCreateScan } from "@/lib/hooks/scan/useCreateScan";
import type { Scan } from "@/types";

export type CreateScanModalRef = {
    open: () => void;
    close: () => void;
};

type CreateScanModalProps = {
    projectId: string;
    onCreated?: (scan: Scan) => void;
};

export const CreateScanModal = forwardRef<
    CreateScanModalRef,
    CreateScanModalProps
>(function CreateScanModal({ projectId, onCreated }, ref) {
    const [opened, setOpened] = useState(false);
    const [branch, setBranch] = useState("");
    const [revision, setRevision] = useState("");

    // The names of the enhancers left out; all of them run at first
    const [skipped, setSkipped] = useState<string[]>([]);

    const {
        enhancers,
        error: enhancersError,
    } = useListEnhancers(opened);

    // What the project's scans leave out, to start from
    const {
        excludes: projectExcludes,
        saveExcludes,
    } = useProjectScanSettings(projectId, opened);

    // The patterns as typed, one a line, once they were changed; the
    // project's own until then
    const [typedExcludes, setTypedExcludes] = useState<string | null>(null);
    const [rememberExcludes, setRememberExcludes] = useState(false);
    const [excludesError, setExcludesError] = useState<string | null>(null);

    const excludesText = typedExcludes ?? (projectExcludes ?? []).join("\n");

    const {
        createScan,
        loading,
        error,
    } = useCreateScan({
        projectId,
    });

    const close = () => {
        setOpened(false);
        setBranch("");
        setRevision("");
        setSkipped([]);
        setTypedExcludes(null);
        setRememberExcludes(false);
        setExcludesError(null);
    };

    const toggle = (name: string) =>
        setSkipped((current) => (current.includes(name) ? current.filter((other) => other !== name) : [...current, name]));

    useImperativeHandle(ref, () => ({
        open: () => setOpened(true),
        close,
    }));

    const save = async () => {
        if (!projectId) {
            return;
        }

        const excludes = excludesText.split("\n").map((pattern) => pattern.trim()).filter(Boolean);

        setExcludesError(null);

        if (rememberExcludes) {
            try {
                // First, so a pattern that is not well formed stops the scan too
                await saveExcludes(excludes);
            } catch (error) {
                setExcludesError(error instanceof Error ? error.message : "Failed to save the patterns");
                return;
            }
        }

        try {
            const scan = await createScan({
                branch,
                revision: revision || undefined,
                // Left out while every enhancer runs, as the API does without
                enhancers: skipped.length > 0
                    ? enhancers.map((enhancer) => enhancer.name).filter((name) => !skipped.includes(name))
                    : undefined,
                // Left out until the project's own are loaded or changed, as
                // the API then uses the project's
                excludes: typedExcludes !== null || projectExcludes !== null ? excludes : undefined,
            });

            onCreated?.(scan);
            close();
        } catch {
            // Error is exposed through the hook.
        }
    };

    return (
        <Dialog
            open={opened}
            title="Create Scan"
            onClose={close}
            footer={
                <>
                    <Button
                        variant="ghost"
                        onClick={close}
                        disabled={loading}
                    >
                        Cancel
                    </Button>

                    <Button
                        variant="primary"
                        onClick={save}
                        disabled={loading || !projectId || !branch.trim()}
                    >
                        {loading ? "Creating..." : "Create Scan"}
                    </Button>
                </>
            }
        >
            <Field label="Branch">
                <TextInput
                    value={branch}
                    onChange={(e) => setBranch(e.target.value)}
                    disabled={loading}
                    placeholder="main"
                />
            </Field>

            <Field
                label="Revision"
                description="A commit, tag or other git revision; the tip of the branch when empty."
            >
                <TextInput
                    value={revision}
                    onChange={(e) => setRevision(e.target.value)}
                    disabled={loading}
                    placeholder="Optional"
                />
            </Field>

            <Field
                label="Leave out files"
                description="One pattern a line, as in a .gitignore: *.spec.ts, generated/, src/**/*.stories.tsx. What these files declare is left out of the scan too."
            >
                <TextArea
                    rows={3}
                    value={excludesText}
                    onChange={(e) => setTypedExcludes(e.target.value)}
                    disabled={loading}
                    placeholder="*.spec.ts"
                    spellCheck={false}
                    className="font-mono text-sm"
                />
            </Field>

            <Checkbox
                label="Remember for this project's next scans"
                checked={rememberExcludes}
                disabled={loading}
                onChange={() => setRememberExcludes(!rememberExcludes)}
            />

            {excludesError && (
                <div role="alert">
                    {excludesError}
                </div>
            )}

            {enhancers.length > 0 && (
                <Field
                    label="Enhancers"
                    description="What runs on the scan's results to add to them, such as the components of a React application."
                >
                    <div className="flex flex-col gap-2">
                        {enhancers.map((enhancer) => (
                            <Checkbox
                                key={enhancer.name}
                                label={`${enhancer.name} ${enhancer.version}`}
                                checked={!skipped.includes(enhancer.name)}
                                disabled={loading}
                                onChange={() => toggle(enhancer.name)}
                            />
                        ))}
                    </div>
                </Field>
            )}

            {enhancersError && (
                <Text size="sm" tone="muted">
                    The enhancers could not be listed; every installed one will run.
                </Text>
            )}

            {error && (
                <div role="alert">
                    {error.message}
                </div>
            )}
        </Dialog>
    );
});