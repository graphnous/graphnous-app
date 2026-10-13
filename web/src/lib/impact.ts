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
