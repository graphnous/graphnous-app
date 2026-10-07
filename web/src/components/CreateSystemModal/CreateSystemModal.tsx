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

import { useCreateSystem } from "@/lib/hooks/system/useCreateSystem";
import type { System } from "@/types";

export type CreateSystemModalRef = {
    open: () => void;
    close: () => void;
};

type CreateSystemModalProps = {
    onCreated?: (system: System) => void;
};

export const CreateSystemModal = forwardRef<
    CreateSystemModalRef,
    CreateSystemModalProps
>(function CreateSystemModal({ onCreated }, ref) {
    const [opened, setOpened] = useState(false);
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");

    const {
        createSystem,
        loading,
        error,
    } = useCreateSystem();

    const close = () => {
        setOpened(false);
        setName("");
        setDescription("");
    };

    useImperativeHandle(ref, () => ({
        open: () => setOpened(true),
        close,
    }));

    const save = async () => {
        try {
            const system = await createSystem({
                name,
                description: description || undefined,
            });

            onCreated?.(system);
            close();
        } catch {
            // Error is exposed through the hook.
        }
    };

    return (
        <Dialog
            open={opened}
            title="Create System"
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
                        disabled={loading || !name.trim()}
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

            {error && (
                <div role="alert">
                    {error.message}
                </div>
            )}
        </Dialog>
    );
});
