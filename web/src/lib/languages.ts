const LABELS: Record<string, string> = {
    JAVA: "Java",
    TYPESCRIPT: "TypeScript",
    JAVASCRIPT: "JavaScript",
    PYTHON: "Python",
    GO: "Go",
    RUST: "Rust",
};

/**
 * How a language of a scan target is written, such as TypeScript for
 * TYPESCRIPT; the name itself for one not known here.
 */
export function languageLabel(language: string): string {
    return LABELS[language] ?? language;
}
