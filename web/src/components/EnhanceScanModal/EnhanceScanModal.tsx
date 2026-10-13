"use client";

import { useState } from "react";

import {
    Alert,
    Button,
    Checkbox,
    Dialog,
    Field,
    Text,
} from "@graphnous/theme";

import { useListEnhancers } from "@/lib/hooks/enhancer/useListEnhancers";
import { useEnhanceScan } from "@/lib/hooks/scan/enhancements/useEnhanceScan";
import type { Scan } from "@/types";

export type EnhanceScanModalProps = {
    scanId: string;
    open: boolean;
    onClose: () => void;
    /**
     * The names of the enhancers chosen for the scan when it was created,
     * which are chosen again at first; null when none were chosen then.
     */
    chosen?: string[] | null;
    /**
     * The names of the enhancers that ran on the scan before: chosen at
     * first for a scan none were chosen for, and every installed enhancer
     * when none ran.
     */
    previous: string[];
    /**
     * Called once enhancing started, in the background.
     */
    onStarted?: (scan: Scan) => void;
};

/**
 * Choosing which enhancers to run again on a completed scan: those chosen
 * when it was created are ticked at first, or else those that ran on it. What a chosen enhancer added
 * before is replaced; what the others added stays.
 */
export function EnhanceScanModal({ scanId, open, onClose, chosen: chosenBefore, previous, onStarted }: EnhanceScanModalProps) {
    const { enhancers, loading: enhancersLoading, error: enhancersError } = useListEnhancers(open);

    // The names ticked, once the choice was changed; the ones chosen for
    // the scan, or that ran on it, until then
    const [changed, setChanged] = useState<string[] | null>(null);

    const { enhanceScan, loading, error, reset } = useEnhanceScan();

    const installed = enhancers.map((enhancer) => enhancer.name);
    const ranBefore = installed.filter((name) => previous.includes(name));
    const atFirst = chosenBefore
        // Without those that are no longer installed
        ? installed.filter((name) => chosenBefore.includes(name))
        : ranBefore.length > 0 ? ranBefore : installed;
    const chosen = changed ?? atFirst;

    const close = () => {
        setChanged(null);
        reset();
        onClose();
    };

    const toggle = (name: string) =>
        setChanged(chosen.includes(name) ? chosen.filter((other) => other !== name) : [...chosen, name]);

    const start = async () => {
        try {
            // In the order they are installed, as they run
            const scan = await enhanceScan(scanId, installed.filter((name) => chosen.includes(name)));

            onStarted?.(scan);
            close();
        } catch {
            // The refusal shows as the error
        }
    };

    return (
        <Dialog
            open={open}
            title="Enhance again"
            onClose={close}
            footer={
                <>
                    <Button variant="ghost" onClick={close} disabled={loading}>
                        Cancel
                    </Button>

                    <Button variant="primary" onClick={start} disabled={loading || chosen.length === 0}>
                        {loading ? "Starting..." : "Enhance"}
                    </Button>
                </>
            }
        >
            <Field
                label="Enhancers"
                description="What a chosen enhancer added to the scan before is replaced; what the others added stays."
            >
                <div className="flex flex-col gap-2">
                    {enhancers.map((enhancer) => (
                        <Checkbox
                            key={enhancer.name}
                            label={
                                previous.includes(enhancer.name)
                                    ? `${enhancer.name} ${enhancer.version} · ran before`
                                    : `${enhancer.name} ${enhancer.version}`
                            }
                            checked={chosen.includes(enhancer.name)}
                            disabled={loading}
                            onChange={() => toggle(enhancer.name)}
                        />
                    ))}

                    {enhancers.length === 0 && !enhancersError && (
                        <Text size="sm" tone="muted">
                            {enhancersLoading ? "Loading the enhancers…" : "No enhancers are installed."}
                        </Text>
                    )}
                </div>
            </Field>

            {enhancersError && (
                <Alert tone="error" title="The enhancers could not be listed">
                    {enhancersError.message}
                </Alert>
            )}

            {error && (
                <Alert tone="error" title="The scan could not be enhanced again" announce>
                    {error.message}
                </Alert>
            )}
        </Dialog>
    );
}
