"use client";

import { useCallback } from "react";

import { useFetch } from "@/lib/hooks/useFetch";

/**
 * The GitHub App the server is: where it comes from, CONFIGURATION or
 * SETUP, none when there is none yet; why GitHub did not answer as it, if
 * it did not; and why setup cannot register one, if it cannot.
 */
export type GitHubAppStatus = {
    source: "CONFIGURATION" | "SETUP" | null;
    appId: string | null;
    slug: string | null;
    name: string | null;
    htmlUrl: string | null;
    installUrl: string | null;
    error: string | null;
    blocked: string | null;
    serverUrl: string | null;
};

export type GitHubAppRegistration = {
    name?: string;
    organization?: string;
    serverUrl?: string;
    webhookUrl?: string;
};

/**
 * The GitHub App's status, null until loaded and when the server has the
 * App off; registering one, which takes the browser to GitHub; and
 * forgetting the one setup registered.
 */
export function useGitHubApp() {
    const { data, loading, error, refresh } = useFetch<GitHubAppStatus>(
        "/api/github/app",
        "Failed to load the GitHub App",
        { notFoundAsEmpty: true },
    );

    /**
     * Posts the App's manifest to GitHub, as a form, which leaves this page:
     * GitHub registers the App as the user confirms, and sends them back
     * to the server, which goes on to installing it. Throws with the API's
     * message when setup cannot start.
     */
    const register = useCallback(async (registration: GitHubAppRegistration) => {
        const response = await fetch("/api/github/app/manifest", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(registration),
        });

        const body = await response.json().catch(() => null);

        if (!response.ok) {
            throw new Error(
                typeof body?.message === "string" && body.message ? body.message : "Failed to start the GitHub App's setup",
            );
        }

        const form = document.createElement("form");
        form.method = "POST";
        form.action = body.action;

        const manifest = document.createElement("input");
        manifest.type = "hidden";
        manifest.name = "manifest";
        manifest.value = JSON.stringify(body.manifest);

        form.appendChild(manifest);
        document.body.appendChild(form);
        form.submit();
    }, []);

    const disconnect = useCallback(async () => {
        const response = await fetch("/api/github/app", { method: "DELETE" });

        if (!response.ok) {
            const body = await response.json().catch(() => null);

            throw new Error(
                typeof body?.message === "string" && body.message ? body.message : "Failed to disconnect the GitHub App",
            );
        }

        await refresh();
    }, [refresh]);

    return { status: data, loading, error, refresh, register, disconnect };
}
