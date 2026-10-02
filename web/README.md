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

Components get the client with `useApi()`, from the `ApiClientProvider` in
the root layout. It calls the API at `NEXT_PUBLIC_GRAPHNOUS_API_URL`, which is
read when the app is built, or else the server in the spec:

```bash
NEXT_PUBLIC_GRAPHNOUS_API_URL=http://localhost:8080 npm run dev
```

## Components and theme

The components and the Tailwind theme are the component library in
[`graphnous-theme`](../../graphnous-theme), which `src/app/globals.css` and the
pages import. This app keeps what knows the API: the client in `src/lib/api`,
the data hooks in `src/lib/hooks`, and `ApiErrorMessage`. `AppLinkProvider`
makes the library's links go through Next.js' router.

Install from the root of the repository, which is an npm workspace with both
packages:

```bash
npm ci
```

## Storybook

The library's components have their own Storybook in `graphnous-theme`; this
app's Storybook has the stories of what stays here:

```bash
npm run storybook         # at http://localhost:6006
npm run test:storybook    # runs every story as a test, with accessibility checks, in light and dark
```

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
