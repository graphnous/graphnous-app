# Impact analysis

What it takes for Graphnous to answer "if I change this, what else do I have
to change?". Written on 2026-10-12, updated on 2026-10-14. Steps 1 and 2 of
the plan are built; step 3 is not.

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
| React component: which props each use passes | the attributes at each JSX site | No | No |
| Java entity: where it is instantiated | every `new Order(...)` site | Yes, by the Java scanner from 0.3.0 | Yes |
| Java method: who calls it | every call site | Yes, by the Java scanner from 0.3.0 | Yes |

- **TypeScript.** Each function and method in a scan result lists its calls,
  constructor calls, field reads and writes, and type uses. Each has the
  target's qualified name, a line number, and whether the scanner resolved it.
  The server drops all of this when it builds the graph.
- **Java.** The Java scanner records calls, constructor calls, field accesses
  and type uses from `0.3.0`, the image the server runs. `0.2.0` and earlier
  did not.
- **Arguments and props.** No scanner records them and the scan-result schema
  has no place for them. This is the same gap that blocks the HTTP links
  between projects.

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

### Step 3: record arguments and props

A schema change and a change in both scanners: the arguments of each call and
the attributes of each JSX element.

Result: "these 14 files use `OrderList`" becomes "these 3 do not pass
`customerId`". It also unblocks the HTTP links between projects, the
`fetch-browser` enhancer, and URLs on Angular HTTP calls.

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
| Step 3 | the props and arguments at each site, so a new required parameter shows the call sites that lack it |

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
