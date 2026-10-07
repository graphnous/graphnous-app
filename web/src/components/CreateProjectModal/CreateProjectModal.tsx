"use client";

import {
    forwardRef,
    useImperativeHandle,
    useState,
} from "react";

import {
    Button,
    Dialog,
    Field,
    TextArea,
    TextInput,
} from "@graphnous/theme";

import { useCreateProject } from "@/lib/hooks/project/useCreateProject";
import type { Project } from "@/types";
import { useSystemStore } from "@/lib/store/systemStore";

export type CreateProjectModalRef = {
    open: () => void;
    close: () => void;
};

type CreateProjectModalProps = {
    onCreated?: (project: Project) => void;
};

export const CreateProjectModal = forwardRef<
    CreateProjectModalRef,
    CreateProjectModalProps
>(function CreateProjectModal({ onCreated }, ref) {
    const [opened, setOpened] = useState(false);
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [gitUrl, setGitUrl] = useState("");

    const selectedSystemId = useSystemStore(
        (state) => state.selectedSystem?.id ?? null,
    );

    if (!selectedSystemId) {
        throw new Error('No system selected');
    }
    const {
        createProject,
        loading,
        error,
    } = useCreateProject({
        systemId: selectedSystemId,
    });

    const close = () => {
        setOpened(false);
        setName("");
        setDescription("");
        setGitUrl("");
    };

    useImperativeHandle(ref, () => ({
        open: () => setOpened(true),
        close,
    }));

    const save = async () => {
        if (!selectedSystemId) {
            return;
        }

        try {
            const project = await createProject({
                name,
                description: description || undefined,
                gitUrl,
            });

            onCreated?.(project);
            close();
        } catch {
            // Error is exposed through the hook.
        }
    };

    return (
        <Dialog
            open={opened}
            title="Create Project"
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
                        disabled={loading || !selectedSystemId || !name.trim()}
                    >
                        {loading ? "Creating..." : "Save"}
                    </Button>
                </>
            }
        >
            <Field label="Name">
                <TextInput
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    disabled={loading}
                />
            </Field>

            <Field label="Description">
                <TextArea
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    disabled={loading}
                />
            </Field>

            <Field label="Git URL">
                <TextInput
                    value={gitUrl}
                    onChange={(e) => setGitUrl(e.target.value)}
                    disabled={loading}
                />
            </Field>

            {error && (
                <div role="alert">
                    {error.message}
                </div>
            )}
        </Dialog>
    );
});