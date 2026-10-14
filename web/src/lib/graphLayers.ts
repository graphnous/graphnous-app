import type { ScanGraphLayer } from "@/types";

/**
 * The id of the layer of what the bodies of methods use: what they call,
 * create, read, write and name. Unlike the other layers, the API shows it
 * only when it is asked for by name, as there is very much of it.
 */
export const USAGES_LAYER = "usages";

/**
 * The layers ticked in a filter, from the layers chosen: every layer but
 * the usages while none were chosen, as the API then shows; null, which
 * the filter reads as all of them, when there is no usages layer to leave
 * out.
 */
export function tickedLayers(layers: ScanGraphLayer[], chosen: string[] | null): string[] | null {
    if (chosen !== null) {
        return chosen;
    }

    return layers.some((layer) => layer.id === USAGES_LAYER)
        ? layers.filter((layer) => layer.id !== USAGES_LAYER).map((layer) => layer.id)
        : null;
}

/**
 * The layers chosen, from what a filter says is ticked: when it says all
 * of them and one is the usages, each by name, as asking for none in
 * particular leaves the usages out.
 */
export function chosenLayers(layers: ScanGraphLayer[], ticked: string[] | null): string[] | null {
    if (ticked !== null) {
        return ticked;
    }

    return layers.some((layer) => layer.id === USAGES_LAYER) ? layers.map((layer) => layer.id) : null;
}
