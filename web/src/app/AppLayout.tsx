"use client";

import { usePathname } from "next/navigation";

import {
    AppShell,
    Icon,
    IconButton,
    NavGroup,
    NavItem,
    Sidebar,
} from "@graphnous/theme";

import {
    ChatCircleIcon,
    FoldersIcon,
    GearIcon,
    GitBranchIcon,
    GraphIcon,
    ScanIcon,
    SirenIcon,
    SparkleIcon,
    SquaresFourIcon,
    UserCircleIcon,
} from "@phosphor-icons/react";

import {
    CreateSystemModal,
    type CreateSystemModalRef,
} from "@/components/CreateSystemModal/CreateSystemModal";
import { SystemSelector } from "@/components/SystemSelector/SystemSelector";
import { useListSystems } from "@/lib/hooks/system/useListSystems";
import { useSystemStore } from "@/lib/store/systemStore";
import { useEffect, useRef } from "react";

const SELECTED_SYSTEM_KEY = "graphnous.selectedSystemId";

export function AppLayout({
    children,
}: {
    children: React.ReactNode;
}) {
    const pathname = usePathname();

    const { systems, loading, error, refresh } = useListSystems();

    const createSystemModal = useRef<CreateSystemModalRef>(null);

    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    const setSelectedSystem = useSystemStore(
        (state) => state.setSelectedSystem,
    );

    // The stored system may have been deleted since. Only checked once the
    // systems have loaded, so a pending or failed request keeps the selection.
    useEffect(() => {
        if (
            selectedSystem &&
            !loading &&
            !error &&
            !systems.some((system) => system.id === selectedSystem.id)
        ) {
            setSelectedSystem(null);
        }
    }, [systems, loading, error, selectedSystem, setSelectedSystem]);

    useEffect(() => {
        if (
            !selectedSystem &&
            systems.length > 0
        ) {
            setSelectedSystem(systems[0]);
        }
    }, [systems, selectedSystem, setSelectedSystem]);

    return (
        <AppShell
            header={
                <div className="flex w-full items-center justify-between">
                    <span className="flex items-center gap-2 font-semibold">
                        <Icon
                            icon={GraphIcon}
                            weight="duotone"
                            className="text-primary"
                        />
                        Graphnous
                    </span>

                    <IconButton
                        icon={UserCircleIcon}
                        label="Account"
                    />
                </div>
            }
            sidebar={
                <Sidebar>
                    <SystemSelector
                        value={selectedSystem?.id ?? null}
                        systems={systems}
                        onSystemChange={(system) => {
                            setSelectedSystem(system ?? null);
                        }}
                        onCreateSystem={() => createSystemModal.current?.open()}
                    />

                    <CreateSystemModal
                        ref={createSystemModal}
                        onCreated={async (system) => {
                            // Selected once it is listed, or the check for
                            // a deleted selection would clear it again
                            await refresh();
                            setSelectedSystem(system);
                        }}
                    />

                    <NavGroup title="Workspace">
                        <NavItem
                            href="/"
                            icon={SquaresFourIcon}
                            active={pathname === "/"}>
                            Overview
                        </NavItem>
                        <NavItem
                            href="/projects"
                            icon={FoldersIcon}
                            active={pathname.startsWith("/projects")}>
                            Projects
                        </NavItem>
                        <NavItem
                            href="/graph"
                            icon={GraphIcon}
                            active={pathname.startsWith("/graph")}>
                            Graph
                        </NavItem>
                        <NavItem
                            href="/dependencies"
                            icon={GitBranchIcon}
                            active={pathname.startsWith("/dependencies")}>
                            Dependencies
                        </NavItem>
                        <NavItem
                            href="/impact-analysis"
                            icon={SirenIcon}
                            active={pathname.startsWith("/impact-analysis")}>
                            Impact Analysis
                        </NavItem>
                        <NavItem
                            href="/scans"
                            icon={ScanIcon}
                            active={pathname.startsWith("/scans")}>
                            Scans
                        </NavItem>
                        <NavItem
                            href="/chat"
                            icon={ChatCircleIcon}
                            active={pathname.startsWith("/chat")}>
                            Chat
                        </NavItem>
                        <NavItem
                            href="/enrichments"
                            icon={SparkleIcon}
                            active={pathname.startsWith("/enrichments")}>
                            Enrichments
                        </NavItem>
                        <NavItem
                            href="/settings"
                            icon={GearIcon}
                            active={pathname.startsWith("/settings")}>
                            Settings
                        </NavItem>
                    </NavGroup>
                </Sidebar>
            }
        >
            {children}
        </AppShell>
    );
}