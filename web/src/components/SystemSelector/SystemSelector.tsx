"use client";

import { Combobox, type ComboboxProps } from "@graphnous/theme";

import type { System } from "@/types";

/**
 * The value of the option that creates a system; not a valid system id.
 */
const CREATE_SYSTEM = "__create-system__";

export type SystemSelectorProps = Omit<ComboboxProps, "options" | "onValueChange"> & {
  systems: System[];
  /**
   * Called with the chosen system.
   */
  onSystemChange: (system: System) => void;
  /**
   * Called when "Create new system" is chosen; without it, the option is
   * not shown.
   */
  onCreateSystem?: () => void;
};

/**
 * Choosing a system, in a Field, by typing to narrow the list down: each
 * option shows the system's name, and its description, shortened, below it.
 * With onCreateSystem, the last option creates a new system.
 */
export function SystemSelector({
  systems,
  onSystemChange,
  onCreateSystem,
  placeholder = "Choose a system",
  emptyMessage = "No systems match",
  ...props
}: SystemSelectorProps) {
  const options = systems.map((system) => ({
    value: system.id,
    label: system.name,
    description: system.description,
  }));

  if (onCreateSystem) {
    options.push({
      value: CREATE_SYSTEM,
      label: "Create new system",
      description: null,
    });
  }

  return (
    <Combobox
      {...props}
      options={options}
      placeholder={placeholder}
      emptyMessage={emptyMessage}
      onValueChange={(id) => {
        if (id === CREATE_SYSTEM) {
          onCreateSystem?.();
          return;
        }

        const system = systems.find((candidate) => candidate.id === id);

        if (system) {
          onSystemChange(system);
        }
      }}
    />
  );
}
