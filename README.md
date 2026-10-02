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

1. Pin the web app's dependencies that are `latest`
   (`@chromatic-com/storybook`, `playwright` and `vite`).

To run the server's image or compose file outside CI, clone the schemas and
both scanners next to the app, as the workflow does.
