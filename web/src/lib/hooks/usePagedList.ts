"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";

export type SortDirection = "asc" | "desc";

export type PagedListOptions<Field extends string> = {
  /**
   * The fields the API can sort this list by.
   */
  sortFields: readonly Field[];
  defaultSort: Field;
  defaultDirection?: SortDirection;
  defaultSize?: number;
  /**
   * The page sizes allowed in the URL.
   */
  sizes?: readonly number[];
  /**
   * Prefixes the URL's parameters, for two lists on one page, such as
   * "scans" for scansPage and scansSort.
   */
  prefix?: string;
};

export type PagedList<Field extends string> = {
  /**
   * Zero-based, as the API counts pages.
   */
  page: number;
  size: number;
  sort: Field;
  direction: SortDirection;
  setPage: (page: number) => void;
  /**
   * Goes back to the first page.
   */
  setSize: (size: number) => void;
  /**
   * Goes back to the first page; takes the Table's sort.
   */
  setSort: (sort: { field: Field; direction: SortDirection }) => void;
};

function parameter(prefix: string | undefined, name: string): string {
  return prefix ? `${prefix}${name[0].toUpperCase()}${name.slice(1)}` : name;
}

/**
 * The page, size, sort and direction of a list, kept in the URL, so a list
 * can be shared and reloaded and the back button goes to the previous page.
 * Values that are not allowed, and the defaults, are left out of the URL.
 *
 * It reads the URL with useSearchParams: a page that prerenders needs a
 * Suspense boundary around the component that uses it.
 */
export function usePagedList<Field extends string>({
  sortFields,
  defaultSort,
  defaultDirection = "asc",
  defaultSize = 20,
  sizes = [10, 20, 50, 100],
  prefix,
}: PagedListOptions<Field>): PagedList<Field> {
  const searchParams = useSearchParams();
  const pathname = usePathname();
  const router = useRouter();

  const names = {
    page: parameter(prefix, "page"),
    size: parameter(prefix, "size"),
    sort: parameter(prefix, "sort"),
    direction: parameter(prefix, "direction"),
  };

  const rawPage = Number(searchParams.get(names.page));
  const rawSize = Number(searchParams.get(names.size));
  const rawSort = searchParams.get(names.sort);
  const rawDirection = searchParams.get(names.direction);

  const current = {
    // The URL counts pages from 1, for people
    page: Number.isInteger(rawPage) && rawPage >= 1 ? rawPage - 1 : 0,
    size: sizes.includes(rawSize) ? rawSize : defaultSize,
    sort: sortFields.find((field) => field === rawSort) ?? defaultSort,
    direction: rawDirection === "asc" || rawDirection === "desc" ? rawDirection : defaultDirection,
  };

  const navigate = (next: typeof current) => {
    const params = new URLSearchParams(searchParams.toString());
    const set = (name: string, value: string | number, fallback: string | number) => {
      if (value === fallback) {
        params.delete(name);
      } else {
        params.set(name, String(value));
      }
    };

    set(names.page, next.page + 1, 1);
    set(names.size, next.size, defaultSize);
    set(names.sort, next.sort, defaultSort);
    set(names.direction, next.direction, defaultDirection);

    const query = params.toString();
    router.push(query ? `${pathname}?${query}` : pathname, { scroll: false });
  };

  return {
    ...current,
    setPage: (page) => navigate({ ...current, page: Math.max(0, page) }),
    setSize: (size) => navigate({ ...current, size, page: 0 }),
    setSort: ({ field, direction }) => navigate({ ...current, sort: field, direction, page: 0 }),
  };
}
