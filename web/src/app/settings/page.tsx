"use client";

import {
    Container,
    PageHeader,
    Stack,
} from "@graphnous/theme";

import { GitHubAppSettings } from "@/components/GitHubAppSettings/GitHubAppSettings";
import { useGitHubApp } from "@/lib/hooks/github/useGitHubApp";
import { useSystemStore } from "@/lib/store/systemStore";
import { breadcrumbs } from "@/lib/breadcrumbs";

export default function SettingsPage() {
    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    const gitHubApp = useGitHubApp();

    return (
        <Container>
            <Stack gap={8}>
                <PageHeader
                    title="Settings"
                    breadcrumbs={breadcrumbs(selectedSystem, { label: "Settings" })}
                    description={`Configure ${selectedSystem?.name || "the system"} and how Graphnous works for it.`}
                />

                <GitHubAppSettings
                    // No status once loaded: the server has the App off, and its API is not there
                    status={gitHubApp.loading && !gitHubApp.status ? null : gitHubApp.status ?? undefined}
                    loading={gitHubApp.loading}
                    onRegister={gitHubApp.register}
                    onDisconnect={gitHubApp.disconnect}
                />
            </Stack>
        </Container>
    );
}
