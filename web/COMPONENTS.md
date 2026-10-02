# Components

The reusable components the web app needs, in the order to build them: first
general, "dumb" components that only render what they are given, then the
components that know about Graphnous. Each layer only uses the layers above it.

Dumb components take everything through props and call back through
callbacks; they do not fetch data, read the URL or know the API. Domain
components may, and are built from the dumb ones.

The dumb components (sections 1 to 6) are the component library in
[`graphnous-theme`](../../graphnous-theme), in
`src/components/<layer>/<Component>/` there, with their stories next to them.
What knows the API (sections 7 and 8) stays in this app. Icons come from
Phosphor (`@phosphor-icons/react/ssr`).

## 1. Primitives

The smallest building blocks, styled with Tailwind and the design tokens in
`graphnous-theme/src/theme.css`, with variants instead of one-off classes.

- [x] **Button**: `variant` (primary, secondary, ghost, danger), `size`,
  `loading` (shows a spinner and disables it), `icon`. **ButtonLink** is the
  same as a link.
- [x] **IconButton**: a button with only an icon, which requires a `label` for
  screen readers.
- [x] **Icon**: one wrapper around the icon set, so icons have a consistent
  size and colour.
- [x] **Link**: a link with the app's styling, and an external variant. It
  goes through the app's router (Next.js `Link`) given with **LinkProvider**,
  and is a plain anchor without one.
- [x] **Text** and **Heading**: the type scale (`size`, `weight`, `muted`),
  so pages do not pick font sizes themselves.
- [x] **Code** and **CodeBlock**: inline code and a code block with monospace text, for ids,
  qualified names, git revisions and log lines.
- [x] **Badge**: a small coloured label with a `tone` (neutral, info, success,
  warning, error) and a solid or soft variant, from the theme's tones. The
  base of every status shown in the app.
- [x] **Spinner**: an indeterminate loading indicator.
- [x] **Divider**.
- [x] **VisuallyHidden**: text only for screen readers.

## 2. Layout

- [x] **Stack** and **Inline**: vertical and horizontal spacing with a gap
  scale, so spacing does not live in every component.
- [x] **Container**: the page width and side padding.
- [x] **Card**: a bordered surface with optional header, body and footer
  (CardHeader, CardBody, CardFooter).
- [x] **PageHeader**: title, description, breadcrumbs and actions (such as
  "New project"), the same on every page.
- [x] **Breadcrumbs**: the path from system to project to scan.
- [x] **AppShell**: the header, navigation and content area around every page.
- [x] **Sidebar** / **NavGroup** / **NavItem**: navigation with an active
  state, which the page passes in.
- [x] **Tabs** (Tabs, TabList, Tab, TabPanel): switching between views of one
  thing, such as a scan's steps, logs and results; the keyboard follows the
  ARIA tabs pattern. The only client component so far.
- [x] **Section**: a titled part of a page.

## 3. Feedback

- [x] **Alert**: an inline message with a `tone`, a title and an optional
  action.
- [x] **Toast** (ToastProvider and useToast): short-lived confirmations and
  errors, such as "Scan deleted". Errors stay until dismissed; the provider
  still has to be added to the root layout.
- [x] **EmptyState**: an icon, a message and an action for a list without
  items ("No scans yet. Start a scan").
- [x] **ErrorState**: a failed load with a retry action.
- [x] **Skeleton**: placeholders while content loads, in the shape of what
  comes.
- [x] **ProgressBar**: determinate progress, such as an upload.
- [x] **Tooltip**: extra detail on hover and focus, such as an exact
  timestamp. Shows above or below its trigger, without moving to stay on
  screen; a positioning library can come with the overlays.

## 4. Overlays

Dialog and Drawer are built on the native `<dialog>`; Popover, Menu and
Tooltip are positioned with Floating UI, so they stay on screen.

- [x] **Dialog**: a modal with a title, content and actions; focus stays inside
  it, and Escape closes it.
- [x] **ConfirmDialog**: a Dialog that asks to confirm, with a danger variant
  for deleting.
- [x] **Drawer**: a panel from the side, for details without leaving a list.
- [x] **Popover** and **Menu** (DropdownMenu): the actions of a row, such as
  "Edit" and "Delete".

## 5. Forms

All inputs support a label, a description, an error message and `disabled`,
through one **Field** wrapper. Select, Checkbox, Switch and RadioGroup use
the browser's own controls, so they work in forms and with the keyboard as
expected; Combobox follows the ARIA combobox pattern with Floating UI.

- [x] **Field**: label, input, description and error, linked for
  accessibility.
- [x] **TextInput**, **TextArea**.
- [x] **Select** and **Combobox**: choosing from a list, with search in the
  Combobox.
- [x] **Checkbox**, **Switch**, **RadioGroup**.
- [x] **FileDrop**: dropping or picking files, several at once, showing each
  file with its size and a way to remove it. The base of uploading scan
  results.
- [x] **Form**: submitting with a loading state, and showing an error from the
  server next to the fields or above the form.
- [x] **SearchInput**: a text input with a search icon and a clear button,
  debounced.

## 6. Data display

- [x] **Table**: columns, rows, sortable headers (`sort` and `direction`, as
  the API's list endpoints take them), row actions and a loading state.
- [x] **Pagination**: page and size controls matching the API's pages
  (`page`, `size`, `totalElements`, `totalPages`).
- [x] **DescriptionList**: label and value pairs, for the details of a system,
  project or scan.
- [x] **Timestamp**: a relative time ("5 minutes ago") with the exact time in
  a tooltip, and nothing for a missing (null) time.
- [x] **Duration**: the time between two timestamps ("2 min 13 s"), and "still
  running" when the end is missing.
- [x] **CopyButton**: copies a value, such as an id, and confirms it.
- [x] **Truncate**: long text shortened with the full text in a tooltip, for
  qualified names and git URLs.
- [x] **StatusIndicator**: a Badge with a dot or icon for a state, with an
  animated variant for work in progress.
- [x] **Timeline** / **Stepper**: an ordered list of steps with a state each.
  The base of a scan's steps.
- [x] **LogViewer**: a virtualised, monospace list of lines with a level each,
  that follows new lines while scrolled to the bottom and stops following when
  the reader scrolls up.

## 7. Data and state patterns

Not visual, but reused by every page that talks to the API. The client and
errors are in `src/lib/api`, the hooks in `src/lib/hooks`, and
ApiErrorMessage in `src/components`.

- [x] **ApiClientProvider**: one configured client from `src/generated/api`,
  with the API's base URL (from `NEXT_PUBLIC_GRAPHNOUS_API_URL`).
- [x] **useApiError** / **ApiErrorMessage**: turns the API's error body
  (`code` and `message`) into a message. `VALIDATION_ERROR` and
  `MALFORMED_REQUEST` show the message, `FORBIDDEN` says the caller may not do
  it, `PLAN_LIMIT` suggests the plan, `NOT_FOUND` offers a way back, and
  `CONFLICT` explains why, such as a scan that is still running.
- [x] **usePagedList**: the page, size, sort and direction of a list,
  kept in the URL so a list can be shared and reloaded.
- [x] **usePolling**: refetching while something is in progress, and stopping
  once it is finished, for scans and their steps.
- [x] **useEventStream**: reading a server-sent event stream, such as a scan's
  live logs, and closing it when the component unmounts.

## 8. Domain components

Built from the components above; these know Graphnous and its API.

### Systems

- [ ] **SystemTable**: the organization's systems, sortable by name and
  creation date, with actions to edit and delete.
- [ ] **SystemForm**: creating and editing a system (name, description).
- [ ] **SystemSummary**: a system's details and its number of projects.
- [ ] **DeleteSystemDialog**: confirms deleting a system and its projects,
  and explains a `409` when one of its projects has a running scan.

### Projects

- [ ] **ProjectTable**: the projects of a system, sortable by name and
  creation date.
- [ ] **ProjectForm**: creating and editing a project: name, description, git
  URL and the path of the project within the repository.
- [ ] **ProjectSummary**: the project's details, its repository and its latest
  scan.
- [ ] **GitUrl**: a git URL shortened to its repository name, linking to it
  when it is a web URL.
- [ ] **DeleteProjectDialog**: confirms deleting a project and its scans,
  and explains a `409` when a scan is still running.

### Scans

- [x] **ScanStatusBadge**: a scan's status: `PENDING`, `QUEUED`, `RUNNING`
  (animated), `COMPLETED` or `FAILED`.
- [ ] **SourceRevision**: the branch and a shortened revision, with the full
  revision to copy.
- [ ] **ScanTable**: the scans of a project, newest first, with status,
  revision, when they were created and how long they took (`startedAt` to
  `completedAt`), refreshing while a scan is active.
- [ ] **StartScanForm**: starting a scan of a branch and revision.
- [ ] **UploadScanResultsForm**: uploading result files scanned elsewhere, one
  per target, with a branch and revision; built on FileDrop. Shows the
  server's reason when a file is refused, and opens the new scan's steps.
- [ ] **ScanSummary**: a scan's status, revision, times and duration.
- [ ] **DeleteScanDialog**: confirms deleting a scan, and explains that a
  running scan cannot be deleted yet.

### Scan execution

- [x] **ScanStepStatusBadge**: a step's status: `PENDING`, `RUNNING`,
  `COMPLETED`, `FAILED` or `SKIPPED`.
- [x] **ScanSteps**: a scan's steps in order (checkout, plan, scan, store,
  enhance results, enhance scan) on the Stepper, each with its status and
  duration, and the error of a failed step. Makes clear that skipped steps did
  not run, such as the first three of an uploaded scan, and that a failed
  enhance step leaves the scan completed.
- [x] **ScanStepError**: the error of a failed step, with room for long
  messages.

### Scan logs

- [x] **ScanLogLevelBadge**: `TRACE`, `DEBUG`, `INFO`, `WARN` or `ERROR`.
- [x] **ScanLog**: a scan's logs on the LogViewer, filterable by level; it
  pages through the logs of a finished scan, and follows the live stream of a
  running one.

### Later

These need API endpoints that do not exist yet.

- [ ] **ScanTargetList**: the targets a scan found (language, build system,
  path) with their modules, files and classes.
- [ ] **GraphView**: the scan's graph, or part of it, as nodes and
  relationships.
- [ ] **NodeDetails**: a class, method or annotation with its properties and
  relationships, including what enhancers added (`<namespace>_...`).
- [ ] **EnhancementSummary**: what the enhancers did to a scan: each enhancer
  and rule, how many nodes it matched, and the rules that left nodes without
  their target. The server only writes this to the scan's log now; it needs
  to be stored with the scan to show it reliably.
- [ ] **EnhancerList**: the installed enhancers, their scope, languages and
  dependencies.
- [ ] **EnhancerManifestEditor**: writing an enhancer manifest, checked
  against its schema, for when systems can upload their own.
