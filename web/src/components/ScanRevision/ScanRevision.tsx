import { Code, Text } from "@graphnous/theme";

export type ScanRevisionProps = {
    /**
     * The commit the scan is of; null until it has checked it out.
     */
    revision?: string | null;
    /**
     * What the scan was asked for; null for the tip of its branch.
     */
    requestedRevision?: string | null;
    className?: string;
};

const COMMIT = /^[0-9a-f]{40}([0-9a-f]{24})?$/;

/**
 * A full commit hash shortened as git does, to its first 7 characters;
 * anything else as it is.
 */
export function shortRevision(revision: string): string {
    return COMMIT.test(revision) ? revision.slice(0, 7) : revision;
}

/**
 * The revision of a scan in a few characters: its commit, after what it
 * was asked for when that was something else, such as a tag; what it was
 * asked for until it has checked out a commit; null for a scan of the tip
 * of a branch it has not checked out yet.
 */
export function revisionLabel({ revision, requestedRevision }: ScanRevisionProps): string | null {
    if (!revision) {
        return requestedRevision || null;
    }

    const commit = shortRevision(revision);

    // A short or full hash of the commit says nothing more
    if (!requestedRevision || revision.startsWith(requestedRevision)) {
        return commit;
    }

    return `${requestedRevision} · ${commit}`;
}

/**
 * The revision of a scan, with the full commit on hover.
 */
export function ScanRevision({ revision, requestedRevision, className }: ScanRevisionProps) {
    const label = revisionLabel({ revision, requestedRevision });

    if (!label) {
        return (
            <Text as="span" size="sm" tone="muted" className={className}>
                tip
            </Text>
        );
    }

    return (
        <span title={revision ?? undefined} className={className}>
            <Code className="text-xs">{label}</Code>
        </span>
    );
}
