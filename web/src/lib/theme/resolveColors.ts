/**
 * Resolves the theme's colour tokens as they apply inside the element, in
 * the current colour scheme, to rgb() a canvas can draw with: the theme's
 * colours use light-dark() and color-mix(), which it cannot.
 *
 * @param tokens custom properties, such as --color-surface
 */
export function resolveColors<T extends string>(element: HTMLElement, tokens: readonly T[]): Record<T, string> {
    const probe = document.createElement("span");
    probe.style.display = "none";
    element.appendChild(probe);

    const canvas = document.createElement("canvas");
    canvas.width = canvas.height = 1;
    const context = canvas.getContext("2d", { willReadFrequently: true });

    const colors = {} as Record<T, string>;

    for (const token of tokens) {
        probe.style.color = `var(${token})`;
        const color = getComputedStyle(probe).color;

        if (!context) {
            colors[token] = color;
            continue;
        }

        // Drawn and read back, which turns any CSS colour into rgb
        context.clearRect(0, 0, 1, 1);
        context.fillStyle = color;
        context.fillRect(0, 0, 1, 1);
        const [r, g, b] = context.getImageData(0, 0, 1, 1).data;

        colors[token] = `rgb(${r}, ${g}, ${b})`;
    }

    probe.remove();

    return colors;
}

/**
 * Calls back when the colour scheme changes, by the system or by
 * data-theme; returns what stops it.
 */
export function onColorSchemeChange(callback: () => void): () => void {
    const scheme = window.matchMedia("(prefers-color-scheme: dark)");
    scheme.addEventListener("change", callback);

    const theme = new MutationObserver(callback);
    theme.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme", "class"] });

    return () => {
        scheme.removeEventListener("change", callback);
        theme.disconnect();
    };
}
