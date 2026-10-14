"use client";

import { useRef, useState, type ReactNode } from "react";
import { CaretUpDownIcon, CheckIcon, XIcon } from "@phosphor-icons/react/ssr";
import {
    autoUpdate,
    flip,
    FloatingPortal,
    offset,
    size,
    useDismiss,
    useFloating,
    useInteractions,
    useListNavigation,
    useRole,
} from "@floating-ui/react";
import { cn, Icon, useField, useFieldProps } from "@graphnous/theme";

export type MultiSelectOption = {
    value: string;
    label: string;
    /**
     * Shown below the label, such as an enhancer's version.
     */
    description?: ReactNode;
};

export type MultiSelectProps = {
    options: MultiSelectOption[];
    /**
     * The chosen values, in the order of the options.
     */
    value: string[];
    onValueChange: (value: string[]) => void;
    /**
     * Shown while nothing is chosen.
     */
    placeholder?: string;
    /**
     * Shown when nothing matches what was typed.
     */
    emptyMessage?: string;
    disabled?: boolean;
    className?: string;
};

/**
 * Where a floating element goes: into the modal dialog its trigger is in,
 * as the rest of the page is inert and behind the dialog; otherwise the
 * body. As the theme's overlays do.
 */
function portalRoot(reference: Element | null): HTMLElement | undefined {
    return reference?.closest("dialog") ?? undefined;
}

/**
 * Choosing any number of a list, in a Field, by typing to narrow it down,
 * as the theme's Combobox chooses one: the chosen ones show as chips, each
 * with a button to remove it. Follows the ARIA combobox pattern with a
 * multi-selectable list: focus stays in the input, the arrow keys move
 * through the options, Enter chooses or unchooses one and keeps the list
 * open, Backspace in an empty input removes the last chip, and Escape
 * closes the list.
 * <p>
 * Built here until the theme has one.
 */
export function MultiSelect({
    options,
    value,
    onValueChange,
    placeholder,
    emptyMessage = "No matches",
    disabled,
    className,
}: MultiSelectProps) {
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState("");
    const [activeIndex, setActiveIndex] = useState<number | null>(null);
    const items = useRef<Array<HTMLElement | null>>([]);
    const input = useRef<HTMLInputElement | null>(null);
    const field = useField();
    const fieldProps = useFieldProps({ disabled });

    const chosen = options.filter((option) => value.includes(option.value));
    const matches = options.filter((option) => option.label.toLowerCase().includes(query.trim().toLowerCase()));

    const {
        refs: { setReference, setPositionReference, setFloating },
        elements,
        floatingStyles,
        context,
    } = useFloating({
        open,
        onOpenChange: (next) => {
            setOpen(next);
            if (!next) {
                setQuery("");
            }
        },
        strategy: "fixed",
        placement: "bottom-start",
        middleware: [
            offset(4),
            flip({ padding: 8 }),
            size({
                padding: 8,
                apply({ rects, availableHeight, elements }) {
                    Object.assign(elements.floating.style, {
                        width: `${rects.reference.width}px`,
                        maxHeight: `${Math.min(availableHeight, 288)}px`,
                    });
                },
            }),
        ],
        whileElementsMounted: autoUpdate,
    });

    const { getReferenceProps, getFloatingProps, getItemProps } = useInteractions([
        useRole(context, { role: "combobox" }),
        useDismiss(context),
        useListNavigation(context, {
            listRef: items,
            activeIndex,
            onNavigate: setActiveIndex,
            virtual: true,
            loop: true,
        }),
    ]);

    // In the order of the options, whatever order they were chosen in
    const change = (values: string[]) =>
        onValueChange(options.map((option) => option.value).filter((other) => values.includes(other)));

    const toggle = (option: MultiSelectOption) =>
        change(value.includes(option.value) ? value.filter((other) => other !== option.value) : [...value, option.value]);

    const remove = (option: MultiSelectOption) => {
        change(value.filter((other) => other !== option.value));
        input.current?.focus();
    };

    return (
        <div
            // The list goes below the whole box, chips and all
            ref={setPositionReference}
            // Dimmed as a whole, chips and all, as a disabled control is
            aria-disabled={disabled || undefined}
            className={cn(
                "relative flex min-h-10 w-full flex-wrap items-center gap-1 rounded-control border border-border-strong bg-surface py-1 pr-9 pl-1 text-sm text-foreground transition-colors",
                "hover:border-foreground-secondary focus-within:outline-2 focus-within:outline-offset-0 focus-within:outline-ring",
                disabled && "cursor-not-allowed opacity-50 hover:border-border-strong",
                className,
            )}
            // A click beside the chips is a click in the input
            onClick={() => input.current?.focus()}
        >
            {chosen.map((option) => (
                <span
                    key={option.value}
                    className="inline-flex max-w-full items-center gap-1 rounded-control border border-border bg-surface-elevated py-0.5 pr-0.5 pl-2 text-xs text-foreground"
                >
                    <span className="truncate">{option.label}</span>
                    <button
                        type="button"
                        aria-label={`Remove ${option.label}`}
                        disabled={disabled}
                        className="inline-flex size-5 items-center justify-center rounded-control text-foreground-secondary hover:bg-surface hover:text-foreground disabled:pointer-events-none"
                        onClick={(event) => {
                            event.stopPropagation();
                            remove(option);
                        }}
                    >
                        <Icon icon={XIcon} size="sm" />
                    </button>
                </span>
            ))}
            <input
                {...fieldProps}
                ref={(element) => {
                    input.current = element;
                    setReference(element);
                }}
                type="text"
                autoComplete="off"
                value={query}
                placeholder={chosen.length === 0 ? placeholder : undefined}
                className="h-7 min-w-24 flex-1 bg-transparent px-2 text-sm text-foreground outline-none placeholder:text-foreground-muted disabled:cursor-not-allowed"
                // floating-ui's prop getters hand it the refs, and read none
                // eslint-disable-next-line react-hooks/refs
                {...getReferenceProps({
                    onChange: (event) => {
                        setQuery((event.currentTarget as HTMLInputElement).value);
                        setOpen(true);
                        setActiveIndex(0);
                    },
                    onClick: () => setOpen(true),
                    onKeyDown: (event) => {
                        if (event.key === "Enter" && open && activeIndex !== null && matches[activeIndex]) {
                            event.preventDefault();
                            toggle(matches[activeIndex]);
                        } else if (event.key === "Backspace" && query === "" && chosen.length > 0) {
                            remove(chosen[chosen.length - 1]);
                        }
                    },
                })}
            />
            <Icon icon={CaretUpDownIcon} size="sm" className="pointer-events-none absolute right-3 text-foreground-muted" />
            {open && (
                <FloatingPortal root={portalRoot(elements.domReference)}>
                    <div
                        ref={setFloating}
                        // So a Dialog around it leaves Escape to it, as the theme's overlays do
                        data-overlay=""
                        aria-labelledby={field?.labelId}
                        aria-multiselectable="true"
                        style={floatingStyles}
                        className="z-50 flex flex-col overflow-y-auto rounded-card border border-border bg-surface p-1 shadow-lg outline-none"
                        {...getFloatingProps()}
                    >
                        {matches.length === 0 && (
                            <div role="option" aria-disabled="true" aria-selected="false" className="px-2 py-1.5 text-sm text-foreground-muted">
                                {emptyMessage}
                            </div>
                        )}
                        {/* eslint-disable-next-line react-hooks/refs -- as getReferenceProps above */}
                        {matches.map((option, index) => (
                            <div
                                key={option.value}
                                ref={(element) => {
                                    items.current[index] = element;
                                }}
                                className={cn(
                                    "flex cursor-default items-center gap-2 rounded-control px-2 py-1.5 text-sm text-foreground",
                                    activeIndex === index && "bg-surface-elevated",
                                )}
                                {...getItemProps({
                                    active: activeIndex === index,
                                    selected: value.includes(option.value),
                                    onClick: () => {
                                        toggle(option);
                                        input.current?.focus();
                                    },
                                })}
                            >
                                <span className="flex min-w-0 flex-1 flex-col">
                                    <span className="truncate">{option.label}</span>
                                    {option.description && (
                                        <span className="truncate text-xs text-foreground-secondary">{option.description}</span>
                                    )}
                                </span>
                                {value.includes(option.value) && <Icon icon={CheckIcon} size="sm" className="text-primary" />}
                            </div>
                        ))}
                    </div>
                </FloatingPortal>
            )}
        </div>
    );
}
