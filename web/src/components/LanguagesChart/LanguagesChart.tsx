"use client";

import { Text } from "@graphnous/theme";
import { ArcElement, Chart, DoughnutController, Tooltip } from "chart.js";
import { useEffect, useRef } from "react";

import { onColorSchemeChange, resolveColors } from "@/lib/theme/resolveColors";

Chart.register(DoughnutController, ArcElement, Tooltip);

/**
 * The languages a scan target can have, in the order of their chart
 * colours, so a language keeps its colour whichever others a project has.
 */
const LANGUAGES = [
    { language: "JAVA", label: "Java", token: "--chart-series-1" },
    { language: "TYPESCRIPT", label: "TypeScript", token: "--chart-series-2" },
    { language: "JAVASCRIPT", label: "JavaScript", token: "--chart-series-3" },
    { language: "PYTHON", label: "Python", token: "--chart-series-4" },
    { language: "GO", label: "Go", token: "--chart-series-5" },
    { language: "RUST", label: "Rust", token: "--chart-series-6" },
] as const;

/**
 * Files of a target without a language, or of one not known here.
 */
const OTHER_TOKEN = "--color-neutral";

const TOKENS = [
    ...LANGUAGES.map(({ token }) => token),
    OTHER_TOKEN,
    "--color-surface",
    "--color-surface-elevated",
    "--color-foreground",
    "--color-border",
] as const;

type Slice = {
    label: string;
    token: (typeof TOKENS)[number];
    files: number;
};

export type LanguagesChartProps = {
    /**
     * The number of files of each language, such as JAVA.
     */
    languages: Record<string, number>;
};

/**
 * The files of a project by language: a donut with the total in its
 * middle, and each language beside it with its files and share.
 */
export function LanguagesChart({ languages }: LanguagesChartProps) {
    const canvas = useRef<HTMLCanvasElement>(null);

    const slices = toSlices(languages);
    const total = slices.reduce((sum, { files }) => sum + files, 0);

    // A key that changes only when the slices do, so the chart is not
    // drawn again for a new object with the same counts
    const key = slices.map(({ label, files }) => `${label}:${files}`).join(",");

    useEffect(() => {
        if (!canvas.current) {
            return;
        }

        const element = canvas.current;
        const drawn = toSlices(languages);

        const chart = new Chart(element, {
            type: "doughnut",
            data: {
                labels: drawn.map(({ label }) => label),
                datasets: [{ data: drawn.map(({ files }) => files), hoverOffset: 4 }],
            },
            options: {
                cutout: "68%",
                maintainAspectRatio: false,
                plugins: {
                    tooltip: {
                        displayColors: true,
                        callbacks: {
                            label: (item) => {
                                const files = item.parsed;

                                return ` ${files.toLocaleString()} ${files === 1 ? "file" : "files"} (${share(files, total)})`;
                            },
                        },
                    },
                },
            },
        });

        // In the colours of the current scheme, and again when it changes
        const style = () => {
            const colors = resolveColors(element, TOKENS);
            const [dataset] = chart.data.datasets;

            dataset.backgroundColor = drawn.map(({ token }) => colors[token]);
            // The surface between the slices sets them apart
            dataset.borderColor = colors["--color-surface"];
            dataset.borderWidth = 2;
            dataset.borderRadius = 2;

            const tooltip = chart.options.plugins!.tooltip!;
            tooltip.backgroundColor = colors["--color-surface-elevated"];
            tooltip.titleColor = colors["--color-foreground"];
            tooltip.bodyColor = colors["--color-foreground"];
            tooltip.borderColor = colors["--color-border"];
            tooltip.borderWidth = 1;

            chart.update();
        };

        style();

        const stopStyling = onColorSchemeChange(style);

        return () => {
            stopStyling();
            chart.destroy();
        };
        // The slices, by their key
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [key]);

    if (total === 0) {
        return <Text>None</Text>;
    }

    return (
        <div className="flex flex-wrap items-center gap-6">
            <div className="relative size-40 shrink-0">
                <canvas
                    ref={canvas}
                    role="img"
                    aria-label={`Files by language: ${slices.map(({ label, files }) => `${label} ${share(files, total)}`).join(", ")}`}
                />
                <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                    <Text as="span" size="lg">{total.toLocaleString()}</Text>
                    <Text as="span">{total === 1 ? "file" : "files"}</Text>
                </div>
            </div>
            <ul className="flex min-w-48 flex-1 flex-col gap-2">
                {slices.map(({ label, token, files }) => (
                    <li key={label} className="flex items-center justify-between gap-4">
                        <span className="flex items-center gap-2">
                            <span
                                aria-hidden
                                className="size-3 shrink-0 rounded-sm"
                                style={{ backgroundColor: `var(${token})` }}
                            />
                            <Text as="span">{label}</Text>
                        </span>
                        <Text as="span">
                            {files.toLocaleString()} {files === 1 ? "file" : "files"} ({share(files, total)})
                        </Text>
                    </li>
                ))}
            </ul>
        </div>
    );
}

/**
 * The languages with files, in the order of their colours; any other
 * language is folded into Other, last.
 */
function toSlices(languages: Record<string, number>): Slice[] {
    const slices: Slice[] = LANGUAGES
        .filter(({ language }) => (languages[language] ?? 0) > 0)
        .map(({ language, label, token }) => ({ label, token, files: languages[language] }));

    const known = new Set<string>(LANGUAGES.map(({ language }) => language));
    const other = Object.entries(languages)
        .filter(([language]) => !known.has(language))
        .reduce((sum, [, files]) => sum + files, 0);

    if (other > 0) {
        slices.push({ label: "Other", token: OTHER_TOKEN, files: other });
    }

    return slices;
}

function share(files: number, total: number): string {
    return `${Math.round(files / total * 100)}%`;
}
