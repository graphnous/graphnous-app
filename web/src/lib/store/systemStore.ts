"use client";

import { System } from "@/types";
import { create } from "zustand";
import { persist } from "zustand/middleware";

type SystemStore = {
    selectedSystem: System | null;
    setSelectedSystem: (system: System | null) => void;
};

export const useSystemStore = create<SystemStore>()(
    persist(
        (set) => ({
            selectedSystem: null,

            setSelectedSystem: (system) =>
                set({
                    selectedSystem: system,
                }),
        }),
        {
            name: "graphnous-system",
        },
    ),
);