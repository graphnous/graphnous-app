# Ideas

What could be built on the graph Graphnous keeps, from close to what exists to
wild. Written on 2026-10-14; see [purpose.md](purpose.md) for what Graphnous is
for, and which of these to start with.

## Close to what exists

1. **Pull request impact report.** Scan the pull request's revision, compare
   it with the main branch, and comment: "this changes `OrderService.create`;
   3 callers in 2 projects, and the web app's checkout route calls it over
   HTTP." The most direct path from what exists to daily value.
2. **API contract break detection.** An endpoint's path or parameters change,
   and Graphnous lists every call that will no longer reach it, in every
   repository.
3. **Dead code and dead endpoints.** Endpoints no call reaches, components
   nothing renders, methods without callers, across repositories.
4. **Onboarding maps.** "Show me how a click on Save becomes a database
   write": a path through the graph, from the browser to the query.
5. **Library upgrade planning.** "We are upgrading Jackson: these 40 classes
   call APIs that changed."
6. **Ownership and blast radius.** CODEOWNERS or git authors on the graph:
   which teams a change touches.
7. **Architecture rules as tests.** "The web app never calls the database
   directly", "domain does not depend on persistence", checked on every scan.
8. **Security reachability.** A vulnerability in a library method: is it
   called at all, and can a public endpoint reach it?

## Wild

- **What-if refactoring.** Describe a change, such as "split this service in
  two", and Graphnous shows the new graph: the calls that would cross the new
  boundary, the endpoints it would need, the cycles it would create.
- **Architecture drift over time.** Replay the scans of a project's history
  and watch the system grow tangled or untangle, as a time-lapse of the
  codebase.
- **The graph as an AI agent's memory.** Coding agents rediscover a
  codebase's structure over and over. Graphnous could be their map: before
  editing something, its callers, its endpoints and the tests that cover it.
- **Living documentation.** An architecture document, sequence diagrams of
  the main flows and an API catalogue, generated from the scans and kept
  current by them rather than by hand.
- **Runtime overlay.** Production traces on the graph: which endpoints are
  hot, which code paths actually run. Dead code becomes provable.
- **An organisation's dependency graph.** For a platform team, every service
  of the organisation in one graph: who depends on whose API, and what
  deprecating one really costs.
- **Migration copilot.** "Move from Angular to React", "from REST to events":
  an order of work planned from the graph, leaves first, with progress
  tracked scan by scan.
- **Code review that knows the system.** Reviewers see not only the diff, but
  the paths through the system the diff lies on.
- **Test gap finder.** Paths from public endpoints to writes of data that no
  test goes through.
- **Explain this incident.** A stack trace or a failing endpoint in, and out
  the path through the services, the recent changes on it, and who made them.
