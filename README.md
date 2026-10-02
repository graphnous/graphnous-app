# graphnous-app

The Graphnous app: what users scan their repositories with and explore the
results in. It logs in through [`graphnous-platform`](../graphnous-platform),
or runs without security, as the open-source container does.

| Folder | What it is |
| --- | --- |
| [`server`](server) | The Spring Boot server: the REST API, scan orchestration and persistence, and the scanner framework the scanners build on |
| [`web`](web) | The web app, in Next.js, built from [`@graphnous/theme`](https://www.npmjs.com/package/@graphnous/theme) |

Each has its README with how to run and test it; the
[repository README](../README.md) describes how the pieces fit together.

## CI and sibling repositories

| File | What it does |
| --- | --- |
| `.github/workflows/ci.yml` | On every pull request and push to main: the server's tests (`mvn verify`), the web app's lint, build and story tests, and the server's Docker image |
| `.github/workflows/publish.yml` | On every push to main and every `v*` tag: the server's Maven modules to [GitHub Packages](https://github.com/orgs/graphnous/packages?repo_name=graphnous-app), as `1.0-SNAPSHOT` from main and as the tag's version (`v1.2.3` publishes `1.2.3`) |

The app builds on its sibling repositories: the server generates code from
[`graphnous-schemas`](https://github.com/graphnous/graphnous-schemas) (the
OpenAPI spec, used by the server and the web app, is downloaded from
[GitHub Pages](https://graphnous.github.io/graphnous-schemas/openapi/v1.yaml)),
and the server's image bundles
[`graphnous-java-scanner`](https://github.com/graphnous/graphnous-java-scanner)
and
[`graphnous-typescript-scanner`](https://github.com/graphnous/graphnous-typescript-scanner).
The workflow checks them out at the root of the workspace, and the app into
`graphnous-app` next to them: the layout the app's relative paths expect.

The scanners are private, so the workflow needs a `GRAPHNOUS_TOKEN` secret:
a token that can read the contents of both scanner repositories.

Still to do:

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
