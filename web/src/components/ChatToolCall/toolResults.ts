/**
 * What the assistant's tools on graphnous-server return, as JSON; see
 * GraphnousTools on the server.
 */

export type SystemSummary = {
    id: string;
    name: string;
    description?: string | null;
};

export type ProjectSummary = {
    id: string;
    name: string;
    description?: string | null;
    gitUrl: string;
    path?: string | null;
};

export type ScanSummary = {
    id: string;
    status: "PENDING" | "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED";
    branch?: string | null;
    revision?: string | null;
    requestedRevision?: string | null;
    createdAt?: string | number | null;
    startedAt?: string | number | null;
};

export type GraphModule = {
    name: string;
    path: string;
    files: number;
    packages: number;
    classes: number;
    methods: number;
    dependencies: number;
};

export type GraphTarget = {
    path: string;
    language?: string | null;
    languageVersion?: string | null;
    buildSystem?: string | null;
    modules: GraphModule[];
};

export type ScanGraphSummary = {
    status: ScanSummary["status"];
    targets: GraphTarget[];
};

export type ClassSummary = {
    name: string;
    qualifiedName: string;
    kind?: string | null;
    module?: string | null;
    file?: string | null;
};

export type Annotation = {
    name: string;
    qualifiedName?: string | null;
    /**
     * The arguments as JSON.
     */
    arguments?: string | null;
    /**
     * The method parameter the annotation is on.
     */
    parameter?: string | null;
};

export type Method = {
    name: string;
    kind?: string | null;
    modifiers: string[];
    returnType?: string | null;
    parameterNames: string[];
    parameterTypes: string[];
    annotations: Annotation[];
};

export type Field = {
    name: string;
    type?: string | null;
    modifiers: string[];
    annotations: Annotation[];
};

export type ClassDetails = {
    name: string;
    qualifiedName: string;
    kind?: string | null;
    modifiers: string[];
    typeParameters: string[];
    module?: string | null;
    file?: string | null;
    packageName?: string | null;
    superClass?: string | null;
    interfaces: string[];
    subtypes: string[];
    annotations: Annotation[];
    methods: Method[];
    fields: Field[];
};

export type AnnotatedElement = {
    kind: "CLASS" | "METHOD" | "FIELD" | "FUNCTION" | "VARIABLE";
    /** Absent for a function or variable declared outside any class */
    className?: string | null;
    member?: string | null;
    annotation: Annotation;
};

export type Dependency = {
    module: string;
    name: string;
    version?: string | null;
    scope?: string | null;
};

/**
 * The tool's result, or the message it failed with: a tool that fails
 * returns the error's message rather than JSON.
 */
export type ParsedResult<T> = { ok: true; value: T } | { ok: false; message: string };

export function parseResult<T>(result: string | undefined): ParsedResult<T> {
    if (result === undefined) {
        return { ok: false, message: "No result" };
    }

    try {
        return { ok: true, value: JSON.parse(result) as T };
    } catch {
        return { ok: false, message: result };
    }
}
