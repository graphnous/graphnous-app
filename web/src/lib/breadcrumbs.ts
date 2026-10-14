import type { Breadcrumb } from "@graphnous/theme";

import type { System } from "@/types";

/**
 * The trail of a page: the selected system, which links to its overview,
 * then the page's own crumbs. Without a system, such as while it loads,
 * the trail starts at the page's own: an empty crumb would only leave a
 * separator.
 */
export function breadcrumbs(system: Pick<System, "name"> | null | undefined, ...crumbs: Breadcrumb[]): Breadcrumb[] {
    return system?.name ? [{ label: system.name, href: "/" }, ...crumbs] : crumbs;
}
