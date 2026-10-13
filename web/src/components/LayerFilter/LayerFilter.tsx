"use client";

import { Button, Checkbox, Popover, Text } from "@graphnous/theme";
import { CaretDownIcon, StackIcon } from "@phosphor-icons/react";

import type { ScanGraphLayer } from "@/types";

export type LayerFilterProps = {
    /**
     * Every layer of the scan's graph: what the scanner found, then what
     * each enhancer added.
     */
    layers: ScanGraphLayer[];
    /**
     * The ids of the layers shown; all of them when null.
     */
    shown: string[] | null;
    /**
     * Called with the layers to show, or null once all of them are.
     */
    onShownChange: (shown: string[] | null) => void;
    disabled?: boolean;
};

/**
 * Choosing which layers of a scan's graph to show, such as only what the
 * Spring enhancer added: a button naming them, which opens a list to tick
 * them on and off.
 */
export function LayerFilter({ layers, shown, onShownChange, disabled }: LayerFilterProps) {
    const isShown = (layer: ScanGraphLayer) => shown === null || shown.includes(layer.id);

    const toggle = (layer: ScanGraphLayer) => {
        const next = layers.filter((other) => (other.id === layer.id ? !isShown(other) : isShown(other)));

        onShownChange(next.length === layers.length ? null : next.map((other) => other.id));
    };

    return (
        <Popover
            label="Layers"
            placement="bottom-end"
            trigger={
                <Button
                    variant="secondary"
                    icon={StackIcon}
                    iconEnd={CaretDownIcon}
                    disabled={disabled || layers.length === 0}
                    fullWidth
                >
                    <span className="truncate">{summary(layers, shown)}</span>
                </Button>
            }
        >
            <div className="flex min-w-56 flex-col gap-3">
                <Text size="sm" weight="medium">Show what</Text>
                {layers.map((layer) => (
                    <Checkbox
                        key={layer.id}
                        label={layer.name}
                        checked={isShown(layer)}
                        onChange={() => toggle(layer)}
                    />
                ))}
                <Button variant="ghost" size="sm" disabled={shown === null} onClick={() => onShownChange(null)}>
                    Show all
                </Button>
            </div>
        </Popover>
    );
}

/**
 * What the button says: all, none, the one shown, or the first and how
 * many more.
 */
export function summary(layers: ScanGraphLayer[], shown: string[] | null): string {
    if (shown === null) {
        return "All layers";
    }

    const names = layers.filter((layer) => shown.includes(layer.id)).map((layer) => layer.name);

    if (names.length === 0) {
        return "No layers";
    }

    return names.length === 1 ? names[0] : `${names[0]} + ${names.length - 1}`;
}
