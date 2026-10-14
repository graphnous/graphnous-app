"use client";

import { useCallback, useEffect, useSyncExternalStore } from "react";

import { THEME_STORAGE_KEY, type ResolvedTheme, type ThemePreference } from "./theme";

/**
 * Tells the other users of the hook in this tab that the choice changed;
 * other tabs learn it from the storage event.
 */
const CHANGE_EVENT = "graphnous-theme-change";

function storedPreference(): ThemePreference {
    try {
        const stored = window.localStorage.getItem(THEME_STORAGE_KEY);

        return stored === "light" || stored === "dark" ? stored : "system";
    } catch {
        // Storage can be unavailable, such as in a private window
        return "system";
    }
}

function subscribeToPreference(onChange: () => void): () => void {
    const onStorage = (event: StorageEvent) => {
        if (event.key === THEME_STORAGE_KEY) {
            onChange();
        }
    };

    window.addEventListener("storage", onStorage);
    window.addEventListener(CHANGE_EVENT, onChange);

    return () => {
        window.removeEventListener("storage", onStorage);
        window.removeEventListener(CHANGE_EVENT, onChange);
    };
}

/**
 * The system's colour scheme; dark when it has none, as the theme has it.
 */
function systemTheme(): ResolvedTheme {
    return window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
}

function subscribeToSystem(onChange: () => void): () => void {
    const query = window.matchMedia("(prefers-color-scheme: light)");
    query.addEventListener("change", onChange);

    return () => query.removeEventListener("change", onChange);
}

/**
 * Shows the colour scheme on the page: data-theme on <html> forces one,
 * and without it the page follows the system's.
 */
function apply(preference: ThemePreference) {
    const root = document.documentElement;

    if (preference === "system") {
        root.removeAttribute("data-theme");
    } else {
        root.setAttribute("data-theme", preference);
    }
}

/**
 * The colour scheme the user chose, the one the page shows, and a way to
 * choose another. The choice is kept in the browser, applies to every tab,
 * and is set before the page is first painted (see themeScript).
 */
export function useTheme() {
    const theme = useSyncExternalStore(subscribeToPreference, storedPreference, () => "system" as const);
    const system = useSyncExternalStore(subscribeToSystem, systemTheme, () => "dark" as const);

    // Also when another tab chose
    useEffect(() => {
        apply(theme);
    }, [theme]);

    const setTheme = useCallback((next: ThemePreference) => {
        try {
            if (next === "system") {
                window.localStorage.removeItem(THEME_STORAGE_KEY);
            } else {
                window.localStorage.setItem(THEME_STORAGE_KEY, next);
            }
        } catch {
            // Not kept, but still shown
        }

        apply(next);
        window.dispatchEvent(new Event(CHANGE_EVENT));
    }, []);

    return {
        /**
         * The colour scheme the user chose.
         */
        theme,
        /**
         * The colour scheme the page shows.
         */
        resolvedTheme: theme === "system" ? system : theme,
        setTheme,
    };
}
