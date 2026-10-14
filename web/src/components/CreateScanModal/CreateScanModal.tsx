"use client";

import {
    forwardRef,
    useImperativeHandle,
    useState,
} from "react";

import {
    Alert,
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
import { useListScanners } from "@/lib/hooks/scanner/useListScanners";
import { languageLabel } from "@/lib/languages";

import { MultiSelect } from "@/components/MultiSelect/MultiSelect";
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

    // The languages the server can scan
    const { scanners } = useListScanners(opened);
    const available = scanners.map((scanner) => scanner.language);

    // What the project's scans leave out and which languages they scan,
    // to start from
    const {
        excludes: projectExcludes,
        languages: projectLanguages,
        saveSettings,
    } = useProjectScanSettings(projectId, opened);

    // The patterns as typed, one a line, once they were changed; the
    // project's own until then
    const [typedExcludes, setTypedExcludes] = useState<string | null>(null);
    const [remember, setRemember] = useState(false);
    const [settingsError, setSettingsError] = useState<string | null>(null);

    const excludesText = typedExcludes ?? (projectExcludes ?? []).join("\n");

    // The languages the project scans; none of them chosen means every one
    const projectScans = projectLanguages && projectLanguages.length > 0
        ? available.filter((language) => projectLanguages.includes(language))
        : available;

    // The languages ticked once they were changed; the project's until then
    const [chosenLanguages, setChosenLanguages] = useState<string[] | null>(null);
    const languages = chosenLanguages ?? projectScans;

    // A scan replaces what the project's views show, so a language the
    // project scans but this scan does not goes missing from them
    const missing = remember ? [] : projectScans.filter((language) => !languages.includes(language));

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
        setChosenLanguages(null);
        setRemember(false);
        setSettingsError(null);
    };


    const toggleLanguage = (language: string) =>
        setChosenLanguages(
            languages.includes(language)
                ? languages.filter((other) => other !== language)
                : available.filter((other) => other === language || languages.includes(other)),
        );

    useImperativeHandle(ref, () => ({
        open: () => setOpened(true),
        close,
    }));

    const save = async () => {
        if (!projectId) {
            return;
        }

        const excludes = excludesText.split("\n").map((pattern) => pattern.trim()).filter(Boolean);

        // None when every language is ticked, so languages the server can
        // scan later are scanned too
        const scanned = languages.length === available.length ? [] : languages;

        setSettingsError(null);

        if (remember) {
            try {
                // First, so a pattern that is not well formed stops the scan too
                await saveSettings({ excludes, languages: scanned });
            } catch (error) {
                setSettingsError(error instanceof Error ? error.message : "Failed to save the project's scan settings");
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
                // Likewise until the project's own and the scanners are loaded
                languages: chosenLanguages !== null || (projectLanguages !== null && available.length > 0)
                    ? scanned
                    : undefined,
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
                        disabled={loading || !projectId || !branch.trim() || (available.length > 0 && languages.length === 0)}
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

            {available.length > 1 && (
                <Field
                    label="Languages"
                    description="The languages to scan; the parts of the project in other languages are left out."
                >
                    <div className="flex flex-col gap-2">
                        {available.map((language) => (
                            <Checkbox
                                key={language}
                                label={languageLabel(language)}
                                checked={languages.includes(language)}
                                disabled={loading}
                                onChange={() => toggleLanguage(language)}
                            />
                        ))}
                    </div>
                </Field>
            )}

            {available.length > 0 && languages.length === 0 && (
                <Text size="sm" tone="muted">
                    Choose at least one language to scan.
                </Text>
            )}

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
                label="Remember the languages and files for this project's next scans"
                checked={remember}
                disabled={loading}
                onChange={() => setRemember(!remember)}
            />

            {missing.length > 0 && languages.length > 0 && (
                <Alert tone="warning" title="This scan replaces the project's current results">
                    {missing.map(languageLabel).join(" and ")} will be missing from the project&apos;s views, usages and
                    impact analysis until a scan includes {missing.length === 1 ? "it" : "them"} again.
                </Alert>
            )}

            {settingsError && (
                <div role="alert">
                    {settingsError}
                </div>
            )}

            {enhancers.length > 0 && (
                <Field
                    label="Enhancers"
                    description="What runs on the scan's results to add to them, such as the components of a React application."
                >
                    <MultiSelect
                        options={enhancers.map((enhancer) => ({
                            value: enhancer.name,
                            label: enhancer.name,
                            description: enhancer.version,
                        }))}
                        value={enhancers.map((enhancer) => enhancer.name).filter((name) => !skipped.includes(name))}
                        onValueChange={(chosen) =>
                            setSkipped(enhancers.map((enhancer) => enhancer.name).filter((name) => !chosen.includes(name)))}
                        placeholder="None"
                        disabled={loading}
                    />
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