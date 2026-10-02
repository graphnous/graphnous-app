"use client";

import { Combobox, type ComboboxProps } from "@graphnous/theme";

import type { System } from "@/types";

export type SystemSelectorProps = Omit<ComboboxProps, "options" | "onValueChange"> & {
  systems: System[];
  /**
   * Called with the chosen system.
   */
  onSystemChange: (system: System) => void;
};

/**
 * Choosing a system, in a Field, by typing to narrow the list down: each
 * option shows the system's name, and its description, shortened, below it.
 */
export function SystemSelector({
  systems,
  onSystemChange,
  placeholder = "Choose a system",
  emptyMessage = "No systems match",
  ...props
}: SystemSelectorProps) {
  const options = systems.map((system) => ({
    value: system.id,
    label: system.name,
    description: system.description,
  }));

  return (
    <Combobox
      {...props}
      options={options}
      placeholder={placeholder}
      emptyMessage={emptyMessage}
      onValueChange={(id) => {
        const system = systems.find((candidate) => candidate.id === id);

        if (system) {
          onSystemChange(system);
        }
      }}
    />
  );
}
