# graphnous-app

The Graphnous app: what users scan their repositories with and explore the
results in. It logs in through [`graphnous-platform`](../graphnous-platform),
or runs without security, as the open-source container does.

| Folder | What it is |
| --- | --- |
| [`server`](server) | The Spring Boot server: the REST API, scan orchestration and persistence, and the scanner framework the scanners build on |
| [`web`](web) | The web app, in Next.js, built from [`graphnous-theme`](../graphnous-theme) |

Each has its README with how to run and test it; the
[repository README](../README.md) describes how the pieces fit together.

## Its own repository

The app is to move to a repository of its own, with `server/` and `web/` at
its root. Its `.github` folder is for that repository: GitHub reads
`.github` at a repository's root only, so here it does nothing.

| File | What it does |
| --- | --- |
| `.github/workflows/ci.yml` | On every pull request and push to main: the server's tests (`mvn verify`), the web app's lint, build and story tests, and the server's Docker image |

The app still builds on part of this repository: the server and the web
app generate code from [`graphnous-schemas`](../graphnous-schemas). The
workflow checks it out from this repository (the `GRAPHNOUS_REPOSITORY`
variable, by default `graphnous/Graphnous-2`) at the root of the workspace,
and the app into `graphnous-app` next to it: the layout the app's
relative paths expect, so they work unchanged. The scanners are images of
their own, published by `graphnous/Graphnous-java-scanner` and
`graphnous/Graphnous-typescript-scanner`; the server runs the versions its
`graphnous.scanners` configuration names.

Before the move:

1. Add a `GRAPHNOUS_TOKEN` secret to the new repository: a token that can
   read this repository's contents, as it is private.
2. Have the web app install `graphnous-theme` from GitHub Packages, once it
   is published (see the theme's README), instead of from this repository's
   npm workspace:
   - depend on `graphnous-theme@npm:@graphnous/theme`, and commit a
     `web/package-lock.json`: the workflow installs with `npm ci`;
   - drop `transpilePackages` from `web/next.config.ts`, as the published
     package is compiled;
   - let the new repository's workflows read the package, in its settings
     on GitHub (Manage Actions access), so the workflow's own token can
     install it.
3. Pin the web app's dependencies that are `latest`
   (`@chromatic-com/storybook`, `playwright` and `vite`).

To build the server's image or compose file outside CI, the schemas have
to be next to the app, as they are here.
