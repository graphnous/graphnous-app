# graphnous-app/server

The Graphnous app's backend: the REST API, scan orchestration and
persistence. Its web app is in [`../web`](../web). See the
[repository README](../../README.md) for how the pieces fit together.

## Modules

| Module | What it holds |
| --- | --- |
| `graphnous-core` | What the scanners and enhancers share: the scan result model, generated from the [published scan result schema](https://graphnous.github.io/graphnous-schemas/schema/scan/scan-result.schema.json), which the build downloads. |
| `graphnous-domain` | The domain model: systems, projects, scans, scan logs and scan stats. |
| `graphnous-application` | The services: authorization checks, creating and deleting systems, projects and scans, running a scan, and failing stuck scans. Depends on repository interfaces only. |
| `graphnous-persistence` | The repositories: systems, projects, scans, logs and scan stats in a relational database (JPA), scan results in Neo4j. |
| `graphnous-api` | The Spring Boot application: REST controllers, configuration and the executable jar. Generates its API models from the [published OpenAPI spec](https://graphnous.github.io/graphnous-schemas/openapi/v1.yaml). |
| `graphnous-security` | Who may call the API (see Security below), authorization, entitlements and capabilities. Authorization and entitlements currently allow everything, and the local deployment has every capability and reports authorization as disabled. |
| `graphnous-scanner` | The scanner framework: target detection, scan planning, the scanner definitions, the Docker sandbox that runs the scanner images, and `graphnous-scanner-cli`, which scans a repository from the command line. |
| `graphnous-enhancer` | The enhancers, which add to what a scan found before it is stored. `graphnous-enhancer-core` holds the `Enhancer` interface, what enhancers return, and the provider that runs them; `graphnous-enhancer-spring` adds the controllers and endpoints of Spring web applications; `graphnous-enhancer-angular` the components, directives, services and modules (`NgModule`) of Angular applications and the HTTP calls they make with `HttpClient`. |
| `graphnous-github` | The GitHub App: its webhooks, the scans of pushes and pull requests, and the impact reports it posts on pull requests (see GitHub App below). |
| `graphnous-mcp` | The tools AI agents may use to look up systems, projects, scans and what the scans found, read-only and as the user they run for: the assistant's, and those of the MCP server for coding agents (see MCP below). |

The relational database is PostgreSQL. Flyway creates and migrates its
schema on startup from `graphnous-api/src/main/resources/db/migration`, and
Hibernate only validates the entities against it: change the schema with a
new migration (`V2__...sql`), never by editing an applied one.

### Publishing

The modules are published to GitHub Packages by
`.github/workflows/publish.yml`, so other builds can depend on the scanner
modules without building the server first. To use them from
another build, add the repository
`https://maven.pkg.github.com/graphnous/graphnous-app` with the id `github`,
and a server `github` in `~/.m2/settings.xml` with a token that can read
packages: GitHub Packages needs one even for reading.

## Running with Docker

```sh
cp .env.example .env   # set NEO4J_PASSWORD and POSTGRES_PASSWORD
docker compose up --build
```

Build from this folder; the image is built from the repository root, as it
also needs the schemas. The scanners are images of their own, which the
server pulls on the Docker daemon when it first scans with them; see
Scanners below. The server runs as a non-root user
that needs the group owning the Docker socket: nothing to set on Docker
Desktop, `DOCKER_GID` on Linux (see `.env.example`).

## Running outside Docker

The server starts containers to check out and scan repositories, and those
containers need to see the checkout. Inside Docker that is a shared volume;
outside it, use a directory on the Docker host instead:

```sh
mvn -f pom.xml package -DskipTests

java -jar graphnous-api/target/graphnous-api-1.0-SNAPSHOT.jar \
  --graphnous.scanner.workspace.type=host \
  --graphnous.scanner.workspace.path=/absolute/path/to/checkouts
```

On Docker Desktop the checkout directory must be in a folder shared with
Docker (under your home directory, for example). If a server in Docker uses
the same Docker daemon, also pass `--graphnous.scanner.instance=<another name>`:
each server removes the containers of its own name on startup. Neo4j and PostgreSQL
are expected where `docker-compose.neo4j.yml` publishes them, at
`bolt://localhost:8687` and `localhost:8432`:

```sh
docker compose -f docker-compose.neo4j.yml up -d
```

Set `NEO4J_URI`, `NEO4J_USERNAME` and `NEO4J_PASSWORD`, or `POSTGRES_HOST`,
`POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER` and `POSTGRES_PASSWORD`, for
other instances.

## Configuration

Set in `graphnous-api/src/main/resources/application.yml`; override with
command-line arguments or environment variables.

| Property | Default | Meaning |
| --- | --- | --- |
| `graphnous.sse.heartbeat-interval` | `PT25S` | How often the open event streams, of notifications and of scan logs, get a comment, so a proxy in between does not close one that is quiet for a while. |
| `graphnous.scans.timeout` | `1h` | An active scan without a status change for this long is marked failed. |
| `graphnous.scans.recovery-interval` | `1m` | How often to look for such scans. |
| `graphnous.scans.retention.days` (`GRAPHNOUS_SCANS_RETENTION_DAYS`) | `-1` | Finished scans older than this many days are deleted, checked every 6 hours. `-1` keeps them. |
| `graphnous.scans.retention.max-per-project` (`GRAPHNOUS_SCANS_RETENTION_MAX_PER_PROJECT`) | `-1` | The most scans a project keeps: creating one more first deletes its oldest finished scans. `-1` keeps them all. |
| `graphnous.scanner.instance` | `graphnous` | The name on the containers this server starts. On startup the server removes the containers of its name left by a previous run, so servers sharing a Docker daemon need different names. |
| `graphnous.scanner.workspace.type` | `volume` | `volume`: a named Docker volume the server has mounted (server in Docker). `host`: a directory on the Docker host (server outside Docker). |
| `graphnous.scanner.workspace.volume` | `graphnous-checkouts` | The volume, for type `volume`. |
| `graphnous.scanner.workspace.path` | `/checkouts` | Where the server sees the checkouts. |
| `graphnous.scanner.git-image` | `alpine/git:2.54.0` | The image checkouts run in. |
| `graphnous.scanner.ssh-key` (`GRAPHNOUS_SSH_KEY`) | none | The private key (its content) to check out private repositories with over SSH, such as `git@github.com:org/repo.git`. |
| `graphnous.scanner.ssh-known-hosts` (`GRAPHNOUS_SSH_KNOWN_HOSTS`) | none | The host keys of the git servers, as in `known_hosts`; required to check out SSH urls with the key, as no other server is trusted; https urls are checked out without either. For example the output of `ssh-keyscan github.com`, after checking its fingerprints against the ones GitHub publishes. |
| `graphnous.scanners.<LANGUAGE>` | `JAVA` and `TYPESCRIPT` | The scanner for each language; see Scanners. |
| `graphnous.ai.openai.api-key` (`OPENAI_API_KEY`) | none | The OpenAI key of the assistant at `/agent`; without one, every run of it ends in an error. |
| `graphnous.ai.openai.model` (`GRAPHNOUS_AI_MODEL`) | `gpt-5` | The OpenAI model of the assistant. |
| `ag-ui.server.path` | `/agent` | Where the assistant is served over [AG-UI](https://docs.ag-ui.com/): a `POST` of a run, answered with its events as server-sent events. |
| `graphnous.security.enabled` (`GRAPHNOUS_SECURITY_ENABLED`) | `false` | Whether the API needs access tokens; see Security. |
| `graphnous.security.issuer-uri` (`GRAPHNOUS_PLATFORM_URL`) | `http://localhost:1338` | The Graphnous platform, which issues the access tokens. |
| `graphnous.security.default-organization-id` | `00000000-0000-0000-0000-000000000000` | The organization of every request with security off. |
| `graphnous.github.enabled` (`GRAPHNOUS_GITHUB_ENABLED`) | `false` | Whether the server is a GitHub App; see GitHub App. |

## Scanners

Each scanner is an image, published by its own repository
(`graphnous/Graphnous-java-scanner`, `graphnous/Graphnous-typescript-scanner`)
to the GitHub Container Registry. `graphnous.scanners` names one per
language:

```yaml
graphnous:
  scanners:
    JAVA:
      image: ghcr.io/graphnous/graphnous-java-scanner:0.4.0
      command: [java, -XX:MaxRAMPercentage=75, -jar, /opt/graphnous/java-scanner.jar,
                --path, "{repository}", --target, "{target}",
                --java-version, "{languageVersion}", --output, "{output}",
                --download-dependencies]
      output: /output/scan-result.json
      mounts:
        - type: volume
          source: graphnous-maven
          target: /maven-repository
    TYPESCRIPT:
      image: ghcr.io/graphnous/graphnous-typescript-scanner:0.3.0
      command: [node, /opt/graphnous/scanner.js,
                --path, "{repository}", --target, "{target}", --output, "{output}",
                --download-dependencies]
      output: /output/scan-result.json
      mounts:
        - type: volume
          source: graphnous-npm
          target: /npm-cache
```

For each target the server starts a container of the image with the
command as its entrypoint, the repository mounted read-only, and an empty
directory for the output that any user can write to. `{repository}` is
where the repository is mounted, `{target}` the target's path in it,
`{languageVersion}` its detected language version (empty when unknown)
and `{output}` the output, which the server reads the result from once
the container has stopped. A target of a language without a scanner fails
to scan. To try another version, change the image's tag; to try a local
build, the image only has to exist on the Docker daemon.

A scanner may also have `mounts`, `environment` and `files`, which only its
own containers get:

| Key | |
|---|---|
| `mounts[].type` | `volume`: the named Docker volume `source`, created on first use. `bind`: the directory or file `source` on the Docker host. |
| `mounts[].source` | The volume name, or the absolute path on the Docker host. |
| `mounts[].target` | Where the container sees it. |
| `mounts[].read-only` | Whether the scanner may only read it; default `false`. |
| `environment` | The environment variables of the container, by name. |
| `files[].source` | A file this server reads for each scan, e.g. one mounted into its container. |
| `files[].target` | Where the container gets a copy of it, readable by any user; an absolute path in a directory. |

Unlike a mount, a file is copied into each container, so it does not have
to exist on the Docker host and is gone with the container. The server
gets the files through the `ScannerFiles` interface, which reads them as
configured here; a server that keeps them elsewhere, such as in a secret
vault per organization, provides its own implementation.

### Libraries of Java projects

The Java scanner resolves calls into the libraries of a project from their
jars. With `--download-dependencies`, it downloads them from Maven Central
into the Maven repository at `/maven-repository`, which the
`graphnous-maven` volume keeps for the next scans. Scanner containers
therefore need outbound HTTPS to `repo.maven.apache.org`.

For private repositories, such as a company Nexus or Artifactory, give the
Java scanner a Maven `settings.xml` at `/maven-settings/settings.xml`: it
downloads from its mirrors and the repositories of its active profiles,
with the credentials of its servers. Keep the file next to the server, e.g.
mounted into its container, and let the server copy it into the scanner
containers:

```yaml
graphnous:
  scanners:
    JAVA:
      # ...
      files:
        - source: /etc/graphnous/maven-settings.xml
          target: /maven-settings/settings.xml
```

The settings can hold the credentials, or refer to them as `${env.NAME}`,
passed as `environment` of the Java scanner.

The libraries the scanner downloads with the settings are kept in the
`graphnous-maven` volume, which every scan uses: a server scanning for
several organizations with different private repositories should give each
its own volume, so no scan resolves against the private libraries of
another.

See the [Java scanner's README](https://github.com/graphnous/Graphnous-java-scanner#libraries)
for what it reads from the settings.

### Packages of TypeScript projects

The TypeScript scanner resolves calls into the npm packages of a project
from their type definitions. With `--download-dependencies`, it downloads
those of the packages the project declares, in the versions its lock file
pins, into the package cache at `/npm-cache`, which the `graphnous-npm`
volume keeps for the next scans. Scanner containers therefore need
outbound HTTPS to `registry.npmjs.org`.

For private registries, such as GitHub Packages, a company Nexus or
Artifactory, give the TypeScript scanner an `.npmrc` at
`/npm-settings/.npmrc`, with its registries, scoped registries and their
credentials, as a file the server copies into the scanner containers:

```yaml
graphnous:
  scanners:
    TYPESCRIPT:
      # ...
      files:
        - source: /etc/graphnous/npmrc
          target: /npm-settings/.npmrc
```

The `.npmrc` can hold the tokens, or refer to them as `${NAME}`, passed as
`environment` of the TypeScript scanner. As with Maven, a server scanning
for several organizations with different private registries should give
each its own `graphnous-npm` volume.

See the [TypeScript scanner's README](https://github.com/graphnous/Graphnous-typescript-scanner#packages)
for what it reads from the `.npmrc`.

## Security

Off by default, as in the open-source container: anyone may call the API,
and everything belongs to one organization,
`graphnous.security.default-organization-id`.

On, the server is a resource server of the Graphnous platform
([`graphnous-platform`](../../graphnous-platform)): every request needs an
access token the platform issued to the Graphnous app (its OAuth client
`graphnous-app`), in an `Authorization: Bearer` header. The user logs in at
the platform and picks one of their organizations there; the token names
it in its `org_id` claim, and the request works in it. A machine, such as
CI, uses an API key of the organization instead: it gets an access token
for the organization at the platform's `/oauth2/token` with the client
credentials grant, the key's client id and secret as the client's, and
that token, marked by its `api_key` claim, has no user. The server checks
the token's signature with the platform's keys, its issuer, its expiry,
that it is for `graphnous-api`, and that it has an organization; it knows
nothing else of the platform, and fetches its keys on the first request,
so the platform need not be up when the server starts.

## GitHub App

The server can be a GitHub App that reports on every pull request what its
change affects: it scans the pull request's head, compares it with a scan
of the commit its branch started from, which it scans too when there is
none, and posts the impact
report as a check run and a comment, edited on every new push. A push to
the default branch scans it, and the repositories the App is installed on
are checked out with its token. It is off unless `graphnous.github.enabled`
(`GRAPHNOUS_GITHUB_ENABLED`) is on.

[GITHUB_APP.md](../GITHUB_APP.md) describes how to create the App, from
Graphnous's settings or by hand, how to develop with it locally through
smee.io, and its configuration.

The webhooks (`POST /github/webhooks`) and the page GitHub sends the browser
back to after registering the App (`GET /github/setup/callback`) need no
access token, with security on too: a delivery's signature, and the
callback's one-time state, are what let them in. Without the App, the
[impact report Action](../actions/impact-report) posts the same comment from
a workflow.

## MCP

The server is an [MCP](https://modelcontextprotocol.io) server at `/mcp`
(streamable HTTP, stateless), so coding agents such as Claude Code can look
things up in Graphnous before they change code: a file they work on, by
the repository's git url and the file's path in it, and what it declares;
the systems, projects and scans, a scan's classes, methods, annotations and
dependencies; what uses a class, method or field, in its project and over
HTTP in the other projects of its system; a system's HTTP endpoints, with
the method that serves each and every call of it, and the calls that reach
none; what one scan of a project changes from another, such as a
branch's from main's, endpoints included; and what that change affects
(`analyzeImpact`): what uses what it removes or changes, in every project
of the system, and the calls that no longer reach an endpoint. The chat
assistant asks an impact agent of its own, on
`graphnous.ai.openai.impact-model`, to rank those risks. The same report is
a pull request comment at `GET /api/v1/scans/{head}/impact-comment`, which
the [impact report Action](../actions/impact-report) posts. With Claude Code, against a server on this machine:

```sh
claude mcp add --transport http graphnous http://localhost:1337/mcp
```

With security on, `/mcp` needs an access token as the rest of the API does;
an agent can use an API key's (see Security), passed with
`--header "Authorization: Bearer <token>"`. The tools are read-only, and
each call is made as the caller of its request. `graphnous-mcp.properties`
in `graphnous-mcp` sets the server's defaults, such as its name and the
instructions it gives agents; `spring.ai.mcp.server.*` overrides them, and
`spring.ai.mcp.server.enabled=false` turns it off.

## Tests

```sh
mvn test
```

Tests that need Docker (the Docker sandbox, git checkout and container
cancellation, and the Neo4j repository tests through Testcontainers) are
skipped when no Docker daemon is reachable. They run as the instance
`graphnous-test`, so they do not remove the containers of a server running on
the same daemon. Controller tests check requests and responses against the
OpenAPI spec the API is generated from.

The build, and the controller tests, take the OpenAPI spec and the scan
result schema from GitHub Pages, as graphnous-schemas publishes them.
Without access to it, or to try changes to them, build and test against a
checkout of `graphnous-schemas` instead, by its absolute path:

```sh
mvn test -Dgraphnous.schemas=/path/to/graphnous-schemas
```

## License

Apache License 2.0; see [LICENSE](LICENSE).
