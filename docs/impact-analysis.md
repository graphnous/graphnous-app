# Impact analysis

What it takes for Graphnous to answer "if I change this, what else do I have
to change?". Written on 2026-10-12, updated on 2026-10-14. Steps 1 and 2 of
the plan are built, and step 3 is built with the schema and the scanners
(Java `0.3.1`, TypeScript `0.1.1`) it needs.

## The goal

See the full flow of a piece of code, so a change shows where it lands:

- **React component**: where it is used and which props each use passes, so
  adding a required prop shows the files to change.
- **Java entity**: where it is instantiated, so a new field shows where it has
  to be set.
- **Java method**: who calls it, so a new parameter shows the call sites to
  update.

## Where things stand

The graph does not store who calls what. It holds structure: targets, modules,
files, packages, classes, methods, fields, annotations, inheritance and
dependencies, plus what enhancers add.

| Case | Needs | Recorded by a scanner | Stored in the graph |
|---|---|---|---|
| React component: where it is used | every `<OrderList />` site, with file and line | Yes, by the TypeScript scanner (JSX is recorded as a call) | No |
| React component: which props each use passes | the attributes at each JSX site | Yes, by the TypeScript scanner from 0.1.1 | Yes |
| Java entity: where it is instantiated | every `new Order(...)` site | Yes, by the Java scanner from 0.3.0 | Yes |
| Java method: who calls it | every call site | Yes, by the Java scanner from 0.3.0 | Yes |

- **TypeScript.** Each function and method in a scan result lists its calls,
  constructor calls, field reads and writes, and type uses. Each has the
  target's qualified name, a line number, and whether the scanner resolved it.
  The server drops all of this when it builds the graph.
- **Java.** The Java scanner records calls, constructor calls, field accesses
  and type uses from `0.3.0`, the image the server runs. `0.2.0` and earlier
  did not.
- **Arguments and props.** Recorded from Java scanner `0.3.1` and
  TypeScript scanner `0.1.1` (step 3), the images the server runs.

## Plan

### Step 1: store usages in the graph (built)

Only this repository changes. Built on 2026-10-12; see "What step 1 built"
below for where it lives and how it differs from this outline.

When a scan is stored, also write relationships from the scan result, each
with the line it happens on:

| Relationship | From | To | Source in the scan result |
|---|---|---|---|
| `CALLS` | method or function | method or function | a call of kind `METHOD` |
| `INSTANTIATES` | method or function | class | a call of kind `CONSTRUCTOR` |
| `READS` / `WRITES` | method or function | field | a field access |
| `USES` | method or function | class | a type use |

Then build the Impact Analysis page, which is empty today:

- pick a class, method, field or component;
- list everything that uses it, grouped by file, with line numbers;
- optionally follow it outward (callers of callers) to a chosen depth.

Result: for a TypeScript or React project, "where is this component used" and
"who calls this function", down to the file and line to edit.

### Step 2: make the Java scanner record calls (built)

The Java scanner does so from `0.3.0`. Step 1 stored most of what it records
as it was; three shapes that only Java has needed a change here, in
`ScanResultGraph`, found by scanning this server with `0.3.0` on 2026-10-14:

- **Record accessors.** A record has `id()` for its component `id` without
  declaring it, so a call of it had no method to point at. The components of
  a record are now fields of it (`kind` `RECORD_COMPONENT`), and such a call
  is a `READS` of the component. An accessor the record declares itself is a
  method, and is called.
- **Constructors a class does not declare.** `new Order(id)` on a record
  names `com.example.Order.Order(java.util.UUID)`; the class was looked up
  after the last dot, which is in the parameters. They are now left off first.
- **Enum constants.** They were not in the graph, so reading `Status.OPEN`
  was dropped. They are now fields of their enum (`kind` `ENUM_CONSTANT`).

On this server's own code that took the usages stored from 5,255 calls, 762
instantiations and 4,481 reads to 5,255 calls, 1,296 instantiations and 7,974
reads. What is still dropped, besides calls into libraries: uses of code the
build generates, which the scanner resolves but does not scan, and `values()`
of an enum, which no source declares.

### Step 3: record arguments and props (built)

A schema change, a change in both scanners, and here.

- **Schema** (`graphnous-schemas`). A call has `arguments`: one per argument,
  in the order written, each the expression as written on one line, cut off
  at 200 characters, with the `name` it is passed under when it is passed by
  name, and whether it is `spread`. They are source, not values.
- **TypeScript scanner.** Records them for calls and `new`, and the
  attributes of a rendered component as named arguments: `customerId={id}`
  is `id` under `customerId`, `disabled` is `true`, `{...props}` is spread.
- **Java scanner.** Records them for calls, `new`, `this(...)` and
  `super(...)`.
- **Here.** A `CALLS` or `INSTANTIATES` relationship keeps what the call
  passes (`arguments`, `argumentNames`, `argumentSpreads`), the usages
  endpoint returns it per usage, and Impact Analysis shows it under each
  place. Where calls pass arguments by name, the places can be narrowed to
  those that do not pass one; a place that spreads something stays, marked,
  as it may pass it.
- **Urls of HTTP calls.** The `fetch-browser` enhancer, which waited for
  arguments, is now built and installed, and the Angular enhancer (`1.1.0`)
  gives its HTTP calls a `url`; both read it from the argument as written.
  That is what the HTTP links between projects match on.
- **Calls through a wrapper, and their methods.** `fetch-browser` (`1.1.0`)
  also takes a call of a function that wraps fetch for an HTTP call, such
  as the web app's `graphnousFetch(path, ...)` and `useFetch(path)`, with
  the url and method the wrapper requests for its arguments. It reads the
  method from the options as written, GET without one. A url given only
  when a condition holds (``id ? `/x/${id}` : null``) is that url, and a
  variable right after the last segment's text (`/scans${search}`) is
  taken for the query. On this repository that took the links found from
  15, all from the browser into Next.js routes and half of them to the
  wrong method, to 68, 24 of them from Next.js routes into the Spring API.
- **Links stored in the graph.** The links between the latest completed
  scans of a system are stored as `(HttpCall)-[:REQUESTS]->(Endpoint)`,
  with how they matched (`PATH` or `SUFFIX`), the version of the matching,
  and whether they cross projects. A scan's links are found again when it
  completes or is enhanced again, against the latest scan of each other
  project; and when they are read and were stored by an earlier version of
  the matching, or not at all, as for scans from before. Links of other
  scans, such as earlier ones chosen to compare, are found when asked for.
- **Impact analysis across HTTP.** Besides what calls a method, Impact
  Analysis lists what requests an endpoint it serves, over the stored
  links: `(target)-[:ENHANCE]->(Endpoint)<-[:REQUESTS]-(HttpCall)<-[:ENHANCE]-(user)`,
  as `REQUESTS` usages with the call's and the endpoint's method and url.
  The user can be in the scan of another project of the system, named on
  the page, and following further goes on in that scan: from a Spring
  method to the Next.js route that calls it, to the browser code that
  requests that route. Only between the latest scans, whose links are
  stored; and only calls the scan sees, so not those of a generated
  OpenAPI client.

Result: "these 14 files use `OrderList`" becomes "these 3 do not pass
`customerId`", and each call site of a Java method shows what it passes.

#### Scans made before step 3

A scan made with an earlier scanner has no arguments: it shows usages as
before, and nothing about what they pass. It needs scanning again, not
storing again, as the results it kept do not have them.

## What step 1 built

- **Writing.** `ScanResultGraph` turns the calls, field accesses and type uses
  of every method and function into `CALLS`, `INSTANTIATES`, `READS`, `WRITES`
  and `USES` relationships, one per place, each with its `line`.
  `ScanResultRepositoryImpl` writes them with the rest of the scan.
- **Only what the scan has.** A usage is kept when the scanner resolved it and
  its target is a node of the same scan target. Calls into libraries are
  dropped. Names are matched by qualified name, so overloads that share one
  resolve to the first.
- **Out of the drawn graph by default.** The graph endpoint neither follows
  nor returns these relationships unless the `usages` layer is asked for by
  name (`?layers=result,usages`). The graph's Layers filter lists it, unticked,
  for scans that have usages. When shown, each kind of use between two nodes
  is one line that says in how many places.
- **Inheritance is followed.** A method is also used where the method of the
  same name in a class above or below its own is used, since a call through
  an interface or base class may run it, and a change to one is a change to
  all. The methods below that override it are listed as usages (`OVERRIDES`).
  A class also lists the classes that extend its subclasses. Methods are
  matched by name only, so overloads count as one, and sibling implementations
  of the same interface are not reached.
- **Reading.** `GET /api/v1/scans/{id}/usages?node=<id>&depth=<1-3>` returns
  every place that uses a class, method or field, with the file and line. For
  a class it includes where its methods and fields are used, and where it is
  extended or implemented. For a node an enhancer added, such as a component,
  it answers for the function or class behind it. A depth above 1 follows what
  calls or creates the users. At most 1000 usages.
- **Page.** Impact Analysis: choose a project and scan, search a class, method
  or function, and see its usages grouped by file.

- **Storing existing scans again.** Enhancing a scan again can store the
  results it kept again first (`{"restore": true}` on
  `POST /api/v1/scans/{id}/enhancements`, "Store the results again first" in
  the Enhance again dialog), as the server stores them now: with usages, for
  a scan stored before they were. It runs as the scan's store step, replaces
  the graph of the scan at once, and removes what every enhancer added, so
  the enhancers chosen run on the new graph.

## What each step delivers

| After | You can see |
|---|---|
| Step 1 | usage sites for TypeScript and React: which files to change, not which props each passes |
| Step 2 | callers and instantiation sites for Java |
| Step 3 | the props and arguments at each site, the places that do not pass a prop, and the urls of fetch and Angular HTTP calls |

## Caveats

- **Existing scans need storing again** to get usages: enhance them again
  with "Store the results again first". Only scans stored since 2026-10-12
  kept their results; older ones need a rescan. Java scans made with a
  scanner before `0.3.0` kept results without usages, and need a rescan too.
- **Graphs get bigger.** Calls are the most numerous relationship in a
  codebase. They should stay out of the default graph view and show only in
  impact analysis, or as a layer that is ticked on.
- **Unresolved calls do not appear.** Calls into libraries, and dynamic
  dispatch the scanner cannot follow, are not usages in the graph.
- **One relationship per call site, not per pair.** A method that calls
  another three times has three places to edit, so each site is kept with its
  line.

## Open decisions

- Whether usages are stored for every scan or only when asked for, given the
  size.
- How far outward to follow by default.
