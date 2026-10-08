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
    TextInput,
} from "@graphnous/theme";

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
    };

    useImperativeHandle(ref, () => ({
        open: () => setOpened(true),
        close,
    }));

    const save = async () => {
        if (!projectId) {
            return;
        }

        try {
            const scan = await createScan({
                branch,
                revision: revision || undefined,
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

            {error && (
                <div role="alert">
                    {error.message}
                </div>
            )}
        </Dialog>
    );
});