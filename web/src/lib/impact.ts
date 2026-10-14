import type { ScanUsage, ScanUsageArgument } from "@/types";

/**
 * The types of node of a scan's graph that hold or are code: something
 * uses them, unlike a file, a module or a library. What enhancers add,
 * such as a component, is of the code it enhances.
 */
const WITHOUT_USAGES = new Set([
    "Scan",
    "ScanTarget",
    "Module",
    "File",
    "Package",
    "Annotation",
    "Dependency",
    "Enhancement",
]);

/**
 * Whether impact analysis can say what uses a node of the type.
 */
export function hasUsages(type: string): boolean {
    return !WITHOUT_USAGES.has(type);
}

/**
 * The page that shows what a change to the node affects.
 *
 * @param name what to call the node there until its usages are loaded
 */
export function impactHref(scanId: string, nodeId: string, name?: string | null): string {
    const search = new URLSearchParams({ scan: scanId, node: nodeId });

    if (name) {
        search.set("name", name);
    }

    return `/impact-analysis?${search}`;
}

/**
 * An argument as it reads where it is passed: the value for one passed by
 * position, label={value} for one passed by name, as a rendered component
 * takes its props, label="text" for a string passed by name, and the dots
 * before one that is spread.
 */
export function writtenArgument(argument: ScanUsageArgument): string {
    if (argument.spread) {
        return argument.name ? `${argument.name}={...${argument.value}}` : `...${argument.value}`;
    }

    if (!argument.name) {
        return argument.value;
    }

    return /^(".*"|'.*')$/.test(argument.value)
        ? `${argument.name}=${argument.value}`
        : `${argument.name}={${argument.value}}`;
}

/**
 * The names the usages pass arguments under, such as the props of a
 * component across the places that render it, by name.
 */
export function argumentNames(usages: ScanUsage[]): string[] {
    const names = new Set<string>();

    for (const usage of usages) {
        for (const argument of usage.arguments ?? []) {
            if (argument.name) {
                names.add(argument.name);
            }
        }
    }

    return [...names].sort((a, b) => a.localeCompare(b));
}

/**
 * Whether the usage passes an argument of the name: "unknown" when it
 * spreads something, which may hold it, and null when the usage is not a
 * call that says what it passes.
 */
export function passes(usage: ScanUsage, name: string): boolean | "unknown" | null {
    if (!usage.arguments) {
        return null;
    }

    if (usage.arguments.some((argument) => argument.name === name)) {
        return true;
    }

    return usage.arguments.some((argument) => argument.spread) ? "unknown" : false;
}
