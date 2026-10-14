"use client";

import { DesktopIcon, MoonIcon, SunIcon } from "@phosphor-icons/react/ssr";
import { IconButton, Menu } from "@graphnous/theme";

import type { ThemePreference } from "@/lib/theme/theme";
import { useTheme } from "@/lib/theme/useTheme";

const THEMES = [
    { value: "system", label: "System", icon: DesktopIcon },
    { value: "light", label: "Light", icon: SunIcon },
    { value: "dark", label: "Dark", icon: MoonIcon },
] as const satisfies ReadonlyArray<{ value: ThemePreference; label: string; icon: typeof SunIcon }>;

/**
 * Chooses the colour scheme: the system's, light or dark. The button shows
 * the one chosen.
 */
export function ThemeToggle() {
    const { theme, setTheme } = useTheme();
    const current = THEMES.find((option) => option.value === theme) ?? THEMES[0];

    return (
        <Menu
            placement="bottom-end"
            trigger={<IconButton icon={current.icon} label={`Theme: ${current.label}`} />}
            items={THEMES.map((option) => ({
                label: option.label,
                icon: option.icon,
                onSelect: () => setTheme(option.value),
            }))}
        />
    );
}
