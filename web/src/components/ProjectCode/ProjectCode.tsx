"use client";

import {
    Alert,
    Badge,
    Code,
    EmptyState,
    Field,
    Link,
    Pagination,
    SearchInput,
    Select,
    Table,
    type Column,
    type Sort,
} from "@graphnous/theme";
import { CodeIcon } from "@phosphor-icons/react";
import { useState } from "react";

import { completedScans, ScanSelector } from "@/components/ScanSelector/ScanSelector";
import { impactHref } from "@/lib/impact";
import { useScanCode } from "@/lib/hooks/scan/code/useScanCode";
import type { Scan, ScanClass, ScanCodeItems, ScanCodeKind, ScanFile, ScanMethod, ScanModule, ScanPackage } from "@/types";

const PAGE_SIZES = [25, 50, 100];

function mono(value: string | null | undefined) {
    return value ? <span className="break-all font-mono text-sm">{value}</span> : "—";
}

function count(value: number | null | undefined) {
    return value === null || value === undefined ? "—" : value.toLocaleString();
}

/**
 * A size in bytes as it reads, such as 1.2 kB.
 */
export function formatSize(bytes: number | null | undefined): string {
    if (bytes === null || bytes === undefined) {
        return "—";
    }

    if (bytes < 1000) {
        return `${bytes} B`;
    }

    return bytes < 1_000_000 ? `${(bytes / 1000).toFixed(1)} kB` : `${(bytes / 1_000_000).toFixed(1)} MB`;
}

/**
 * The method as it is called: its name and the types of its parameters.
 */
export function signature(method: ScanMethod): string {
    const parameters = method.parameterNames.map((name, index) => {
        const type = method.parameterTypes[index];

        return type ? `${name}: ${type}` : name;
    });

    return `${method.name}(${parameters.join(", ")})`;
}

/**
 * How one kind of code is listed: what to call it, what the API sorts it
 * by, and its columns. A column sorts when its key is one of the sorts.
 */
type Listing<Kind extends ScanCodeKind> = {
    label: string;
    sort: string;
    columns: Column<ScanCodeItems[Kind]>[];
};

const LISTINGS: { [Kind in ScanCodeKind]: Listing<Kind> } = {
    modules: {
        label: "Modules",
        sort: "path",
        columns: [
            { key: "path", header: "Path", sortable: true, cell: (module: ScanModule) => mono(module.path) },
            { key: "name", header: "Name", sortable: true, cell: (module) => module.name ?? "—" },
            { key: "target", header: "Target", cell: (module) => mono(module.target) },
            { key: "files", header: "Files", align: "end", cell: (module) => count(module.files) },
            { key: "classes", header: "Classes", align: "end", cell: (module) => count(module.classes) },
            { key: "methods", header: "Methods", align: "end", cell: (module) => count(module.methods) },
            { key: "dependencies", header: "Dependencies", align: "end", cell: (module) => count(module.dependencies) },
        ],
    },
    packages: {
        label: "Packages",
        sort: "qualifiedName",
        columns: [
            { key: "qualifiedName", header: "Package", sortable: true, cell: (pkg: ScanPackage) => mono(pkg.qualifiedName) },
            { key: "module", header: "Module", cell: (pkg) => mono(pkg.module) },
            { key: "classes", header: "Classes", align: "end", cell: (pkg) => count(pkg.classes) },
            { key: "functions", header: "Functions", align: "end", cell: (pkg) => count(pkg.functions) },
            { key: "variables", header: "Variables", align: "end", cell: (pkg) => count(pkg.variables) },
        ],
    },
    files: {
        label: "Files",
        sort: "path",
        columns: [
            { key: "path", header: "Path", sortable: true, cell: (file: ScanFile) => mono(file.path) },
            { key: "module", header: "Module", cell: (file) => mono(file.module) },
            {
                key: "language",
                header: "Language",
                sortable: true,
                cell: (file) => (file.language ? <Badge>{file.language}</Badge> : "—"),
            },
            {
                key: "sourceSet",
                header: "Source set",
                cell: (file) => (file.sourceSet ? <Badge tone={file.sourceSet === "TEST" ? "info" : "neutral"}>{file.sourceSet}</Badge> : "—"),
            },
            { key: "size", header: "Size", sortable: true, align: "end", cell: (file) => formatSize(file.size) },
            { key: "classes", header: "Classes", align: "end", cell: (file) => count(file.classes) },
            { key: "functions", header: "Functions", align: "end", cell: (file) => count(file.functions) },
        ],
    },
    classes: {
        label: "Classes",
        sort: "qualifiedName",
        columns: [
            { key: "name", header: "Name", sortable: true, cell: (type: ScanClass) => <span className="font-medium">{type.name}</span> },
            { key: "kind", header: "Kind", sortable: true, cell: (type) => (type.kind ? <Badge>{type.kind}</Badge> : "—") },
            { key: "qualifiedName", header: "Qualified name", sortable: true, cell: (type) => mono(type.qualifiedName) },
            { key: "superClass", header: "Extends", cell: (type) => (type.superClass ? <Code className="text-xs">{type.superClass}</Code> : "—") },
            { key: "module", header: "Module", cell: (type) => mono(type.module) },
            { key: "methods", header: "Methods", align: "end", cell: (type) => count(type.methods) },
            { key: "fields", header: "Fields", align: "end", cell: (type) => count(type.fields) },
        ],
    },
    methods: {
        label: "Methods",
        sort: "qualifiedName",
        columns: [
            { key: "name", header: "Method", sortable: true, cell: (method: ScanMethod) => mono(signature(method)) },
            { key: "kind", header: "Kind", sortable: true, cell: (method) => (method.kind ? <Badge>{method.kind}</Badge> : "—") },
            { key: "returnType", header: "Returns", cell: (method) => (method.returnType ? <Code className="text-xs">{method.returnType}</Code> : "—") },
            { key: "className", header: "Class", cell: (method) => mono(method.className) },
            { key: "file", header: "File", cell: (method) => mono(method.file) },
        ],
    },
};

const KINDS = (Object.keys(LISTINGS) as ScanCodeKind[]).map((kind) => ({ value: kind, label: LISTINGS[kind].label }));

export type ProjectCodeProps = {
    /**
     * The project's scans; the code is that of one of the completed ones,
     * the newest at first.
     */
    scans: Scan[];
    /**
     * The scan to show the code of, and only that one: without choosing
     * another, such as on the page of the scan itself.
     */
    scanId?: string;
};

/**
 * What a project's scan found in the code, a kind at a time: its modules,
 * packages, files, classes or methods, searchable and sortable.
 */
export function ProjectCode({ scans, scanId: onlyScanId }: ProjectCodeProps) {
    const [chosenScanId, setChosenScanId] = useState<string | null>(null);
    const scanId = onlyScanId ?? chosenScanId ?? completedScans(scans)[0]?.id ?? null;

    const [kind, setKind] = useState<ScanCodeKind>("classes");
    const [query, setQuery] = useState("");
    const [sort, setSort] = useState<Sort>({ field: LISTINGS.classes.sort, direction: "asc" });
    const [pageNumber, setPageNumber] = useState(0);
    const [size, setSize] = useState(50);

    const listing = LISTINGS[kind] as Listing<ScanCodeKind>;

    const { page, loading, error } = useScanCode({
        scanId,
        kind,
        query,
        page: pageNumber,
        size,
        sort: sort.field,
        direction: sort.direction,
    });

    if (!scanId) {
        return (
            <EmptyState
                icon={CodeIcon}
                title="No completed scans yet"
                description="The code is what a scan found. Scan the project, and once the scan completes, its code shows here."
            />
        );
    }

    const chooseKind = (next: ScanCodeKind) => {
        setKind(next);
        // Each kind sorts by properties of its own
        setSort({ field: LISTINGS[next].sort, direction: "asc" });
        setPageNumber(0);
    };

    const searching = query.trim() !== "";

    // Classes and methods are what something uses; each links to what a
    // change to it affects
    const columns: Column<ScanCodeItems[ScanCodeKind]>[] = kind === "classes" || kind === "methods"
        ? [
            ...listing.columns,
            {
                key: "impact",
                header: "",
                align: "end",
                cell: (item) => (
                    <Link href={impactHref(scanId, item.id, "name" in item ? item.name : null)} className="whitespace-nowrap">
                        Impact
                    </Link>
                ),
            },
        ]
        : listing.columns;

    return (
        <div className="space-y-4 pt-4">
            <div className="flex flex-wrap items-end gap-3">
                <div className="w-40">
                    <Field label="Show">
                        <Select
                            options={KINDS}
                            value={kind}
                            onChange={(event) => chooseKind(event.target.value as ScanCodeKind)}
                        />
                    </Field>
                </div>
                <div className="w-72">
                    <SearchInput
                        // Starts empty for another kind
                        key={kind}
                        label={`Search ${listing.label.toLowerCase()}`}
                        placeholder={`Search ${listing.label.toLowerCase()}`}
                        defaultValue={query}
                        onSearch={(value) => {
                            setQuery(value);
                            setPageNumber(0);
                        }}
                    />
                </div>
                {!onlyScanId && (
                    <div className="ml-auto w-72">
                        <Field label="Scan">
                            <ScanSelector
                                scans={scans}
                                value={scanId}
                                onScanChange={(scan) => {
                                    setChosenScanId(scan.id);
                                    setPageNumber(0);
                                }}
                            />
                        </Field>
                    </div>
                )}
            </div>

            {error && (
                <Alert tone="error" title={`The ${listing.label.toLowerCase()} could not be loaded`} announce>
                    {error.message}
                </Alert>
            )}

            <Table
                caption={listing.label}
                rows={page.content as ScanCodeItems[ScanCodeKind][]}
                rowKey={(item) => item.id}
                columns={columns}
                sort={sort}
                onSortChange={(next) => {
                    setSort(next);
                    setPageNumber(0);
                }}
                loading={loading}
                empty={
                    <EmptyState
                        icon={CodeIcon}
                        title={searching ? `No ${listing.label.toLowerCase()} match` : `No ${listing.label.toLowerCase()}`}
                        description={
                            searching
                                ? `The scan found no ${listing.label.toLowerCase()} with this in their name.`
                                : `The scan found no ${listing.label.toLowerCase()}.`
                        }
                    />
                }
            />

            {page.totalElements > 0 && (
                <Pagination
                    page={page.page}
                    size={size}
                    totalElements={page.totalElements}
                    totalPages={page.totalPages}
                    sizes={PAGE_SIZES}
                    onPageChange={setPageNumber}
                    onSizeChange={(next) => {
                        setSize(next);
                        setPageNumber(0);
                    }}
                />
            )}
        </div>
    );
}
