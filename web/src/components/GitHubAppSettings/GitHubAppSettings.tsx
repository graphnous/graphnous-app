"use client";

import { Alert, Badge, Button, Card, CardBody, CardHeader, Field, Link, Skeleton, Text, TextInput } from "@graphnous/theme";
import { GithubLogoIcon } from "@phosphor-icons/react";
import { useState } from "react";

import type { GitHubAppRegistration, GitHubAppStatus } from "@/lib/hooks/github/useGitHubApp";

export type GitHubAppSettingsProps = {
    /**
     * Null while it loads; undefined when the server has the App off.
     */
    status: GitHubAppStatus | null | undefined;
    loading?: boolean;
    onRegister: (registration: GitHubAppRegistration) => Promise<void>;
    onDisconnect: () => Promise<void>;
};

/**
 * The GitHub App that reports on pull requests what they affect: what it
 * is, and registering one on GitHub, which takes the browser there.
 */
export function GitHubAppSettings({ status, loading, onRegister, onDisconnect }: GitHubAppSettingsProps) {
    const [name, setName] = useState("Graphnous");
    const [organization, setOrganization] = useState("");
    const [serverUrl, setServerUrl] = useState("");
    const [webhookUrl, setWebhookUrl] = useState("");
    const [busy, setBusy] = useState(false);
    const [failure, setFailure] = useState<string | null>(null);

    const run = async (action: () => Promise<void>) => {
        setBusy(true);
        setFailure(null);

        try {
            await action();
        } catch (error) {
            setFailure(error instanceof Error ? error.message : String(error));
        } finally {
            setBusy(false);
        }
    };

    const header = (
        <CardHeader
            title={
                <span className="flex items-center gap-2">
                    <GithubLogoIcon className="size-5" aria-hidden />
                    GitHub App
                </span>
            }
            description="Scans every pull request and comments what its change affects, across projects and over HTTP."
        />
    );

    if (loading && status === null) {
        return (
            <Card>
                {header}
                <CardBody>
                    <Skeleton className="h-16" />
                </CardBody>
            </Card>
        );
    }

    if (status === undefined) {
        return (
            <Card>
                {header}
                <CardBody>
                    <Text size="sm" tone="muted">
                        The server has the GitHub App off. Set <code>GRAPHNOUS_GITHUB_ENABLED=true</code> to set it up here.
                    </Text>
                </CardBody>
            </Card>
        );
    }

    if (status?.source) {
        return (
            <Card>
                {header}
                <CardBody className="flex flex-col gap-3">
                    <div className="flex flex-wrap items-center gap-2">
                        <Text weight="semibold">{status.name ?? `App ${status.appId}`}</Text>
                        {status.error
                            ? <Badge tone="error">Not answering</Badge>
                            : <Badge tone="success">Connected</Badge>}
                        <Badge tone="neutral">{status.source === "CONFIGURATION" ? "From configuration" : "Set up here"}</Badge>
                    </div>

                    {status.error && (
                        <Alert tone="error" title="GitHub did not answer as the App">
                            {status.error}
                        </Alert>
                    )}

                    <div className="flex flex-wrap gap-4 text-sm">
                        {status.installUrl && <Link external href={status.installUrl}>Install it on repositories</Link>}
                        {status.htmlUrl && <Link external href={status.htmlUrl}>Its page on GitHub</Link>}
                    </div>

                    {failure && (
                        <Alert tone="error" title="The App could not be disconnected" announce>
                            {failure}
                        </Alert>
                    )}

                    {status.source === "SETUP" && (
                        <div className="flex justify-end">
                            <Button variant="ghost" size="sm" loading={busy} onClick={() => run(onDisconnect)}>
                                Disconnect
                            </Button>
                        </div>
                    )}
                </CardBody>
            </Card>
        );
    }

    return (
        <Card>
            {header}
            <CardBody className="flex flex-col gap-3">
                {status?.blocked ? (
                    <Alert tone="warning" title="The App cannot be set up here yet">
                        {status.blocked}
                    </Alert>
                ) : (
                    <Text size="sm" tone="muted">
                        Registers a GitHub App for this server on GitHub, where you confirm it, and then install it on
                        the repositories to report on.
                    </Text>
                )}

                <Field label="Name" description="The App's name on GitHub, which no other App may have.">
                    <TextInput value={name} onChange={(e) => setName(e.target.value)} disabled={busy} />
                </Field>

                <Field label="GitHub organization" description="Where to register it; your own account when empty.">
                    <TextInput
                        value={organization}
                        onChange={(e) => setOrganization(e.target.value)}
                        disabled={busy}
                        placeholder="Optional"
                    />
                </Field>

                {!status?.serverUrl && (
                    <Field label="Server URL" description="Where your browser reaches this Graphnous server.">
                        <TextInput
                            value={serverUrl}
                            onChange={(e) => setServerUrl(e.target.value)}
                            disabled={busy}
                            placeholder="https://graphnous.example.com"
                        />
                    </Field>
                )}

                <Field
                    label="Webhook URL"
                    description="Where GitHub delivers the webhooks, when it cannot reach the server itself, such as a smee.io channel."
                >
                    <TextInput
                        value={webhookUrl}
                        onChange={(e) => setWebhookUrl(e.target.value)}
                        disabled={busy}
                        placeholder="Optional"
                    />
                </Field>

                {failure && (
                    <Alert tone="error" title="The setup could not start" announce>
                        {failure}
                    </Alert>
                )}

                <div className="flex justify-end">
                    <Button
                        variant="primary"
                        size="sm"
                        loading={busy}
                        disabled={Boolean(status?.blocked) || !name.trim() || (!status?.serverUrl && !serverUrl.trim())}
                        onClick={() => run(() => onRegister({
                            name: name.trim(),
                            organization: organization.trim() || undefined,
                            serverUrl: serverUrl.trim() || undefined,
                            webhookUrl: webhookUrl.trim() || undefined,
                        }))}
                    >
                        Create on GitHub
                    </Button>
                </div>
            </CardBody>
        </Card>
    );
}
