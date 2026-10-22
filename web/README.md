# graphnous-app/web

The Graphnous app's web app, built with [Next.js](https://nextjs.org). It
calls the app's server, in [`../server`](../server).

## API client

The models and client for the Graphnous API are generated from the
[OpenAPI spec](https://graphnous.github.io/graphnous-schemas/openapi/v1.yaml)
published on GitHub Pages into `src/generated/api` with
[OpenAPI Generator](https://openapi-generator.tech) (`typescript-fetch`),
configured in `openapitools.json`. The generated code is not committed:
`npm run dev` and `npm run build` generate it first, so it always matches the
spec. Generate it on its own with:

```bash
npm run generate:api
```

OpenAPI Generator runs on Java, which has to be installed, and downloads the
spec, so generating needs network access.

The browser only calls the web app: its routes in `src/app/api` call the
server, at `GRAPHNOUS_API_URL`, or else the server in the spec. It is read
when the web app's server starts, not when it is built, so one build runs
against any server. Locally, set it in `.env.local`, which Next.js loads and
git ignores, to the server on this machine:

```ini
GRAPHNOUS_API_URL=http://localhost:1337
```

Components get the client with `useApi()`, from the `ApiClientProvider` in
the root layout. It calls the web app's own `/api/v1`, which passes the API's
reads on to the server (`src/app/api/v1`).

## Components and theme

The components and the Tailwind theme are the component library
[`@graphnous/theme`](https://www.npmjs.com/package/@graphnous/theme), from
[`graphnous-theme`](https://github.com/graphnous/graphnous-theme), which
`src/app/globals.css` and the pages import. This app keeps what knows the API: the client in `src/lib/api`,
the data hooks in `src/lib/hooks`, and `ApiErrorMessage`. `AppLinkProvider`
makes the library's links go through Next.js' router.

Install with:

```bash
npm ci
```

## Storybook

The library's components have their own Storybook in
[`graphnous-theme`](https://github.com/graphnous/graphnous-theme); this
app's Storybook has the stories of what stays here:

```bash
npm run storybook         # at http://localhost:6006
npm run test:storybook    # runs every story as a test, with accessibility checks, in light and dark
```

## Running

```bash
npm run dev     # at http://localhost:3000
```

The server has to run too; see [`../server`](../server).

## Docker

The image runs the production build. Build it from this folder:

```bash
docker build -t graphnous-web .
docker run -p 3000:3000 -e GRAPHNOUS_API_URL=http://host.docker.internal:1337 graphnous-web
```

The build generates the API client, so it downloads the OpenAPI spec and
OpenAPI Generator. `GRAPHNOUS_API_URL` is where the web app's server reaches
the API, such as `http://server:1337` on a compose network; the browser never
calls it.
