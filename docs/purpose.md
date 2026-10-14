# What Graphnous is for

Written on 2026-10-14. For the ideas this leads to, see [ideas.md](ideas.md).

## The point

Graphnous turns a company's code into a living map of how its parts connect,
across languages, repositories and teams. It then answers "what is this, what
touches it, and what breaks if I change it?" without anyone having to read the
code first.

Most tools look at one repository, or one file at a time. Graphnous already
links a React hook to a Next.js route, to the Spring controller it calls, to
the query behind that, across projects. That cross-cutting map is the asset;
everything else is a way to use it.

## What it does today

- **A system's shape.** Projects, modules, classes and their dependencies,
  as a graph and as system views.
- **Frameworks.** Enhancers find Spring endpoints, React components, Next.js
  routes, and Angular services and HTTP calls.
- **HTTP across projects.** Which frontend call requests which backend
  endpoint. With the `fetch-wrappers` branch, not merged yet, also through
  wrappers of fetch such as `graphnousFetch`, and by method.
- **Impact analysis.** Who calls a method, who renders a component and with
  which props, and which places do not pass a prop; see
  [impact-analysis.md](impact-analysis.md).
- **Scan comparison.** What changed between two revisions.
- **Libraries.** Which projects use which libraries.
- **Chat.** Questions about the graph, answered by an assistant.

## The question it answers

The common thread is **"what happens if I change this?"**, asked across
repository and language boundaries, where other tools stop. A use is worth
building when it answers that question for someone at the moment they are
about to change something.

## Where to focus

Pick one sharp use and make it excellent end to end before adding breadth.
Two are closest to what exists, and deliver value every day without anyone
having to open the app:

1. **A pull request impact report**: scan the pull request's revision, compare
   it with the main branch, and comment what it changes and what uses that,
   across projects and over HTTP.
2. **API contract break detection**: when an endpoint's path or parameters
   change, list every call that will no longer reach it, in every repository.

Both are described further in [ideas.md](ideas.md).
