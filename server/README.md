# graphnous-app/server

The Graphnous app's backend: the REST API, scan orchestration and
persistence. Its web app is in [`../web`](../web). See the
[repository README](../../README.md) for how the pieces fit together.

## Modules

| Module | What it holds |
| --- | --- |
| `graphnous-domain` | The domain model: systems, projects, scans and scan logs. |
| `graphnous-application` | The services: authorization checks, creating and deleting systems, projects and scans, running a scan, and failing stuck scans. Depends on repository interfaces only. |
| `graphnous-persistence` | The repositories: systems, projects, scans and logs in a relational database (JPA), scan results in Neo4j. |
| `graphnous-api` | The Spring Boot application: REST controllers, configuration and the executable jar. Generates its API models from the [published OpenAPI spec](https://graphnous.github.io/graphnous-schemas/openapi/v1.yaml). |
| `graphnous-security` | Who may call the API (see Security below), authorization and entitlements. Authorization and entitlements currently allow everything. |
| `graphnous-scanner` | The scanner framework: target detection, scan planning, the scan result model (generated from `graphnous-schemas/scan`), the Java and TypeScript scanner definitions, and the Docker sandbox that runs them. |

The relational database is an in-memory H2 database, so systems, projects
and scans do not survive a restart; scan results in Neo4j do.

## Running with Docker

```sh
cp .env.example .env   # set NEO4J_PASSWORD
docker compose up --build
```

Build from this folder; the image is built from the repository root, as it
also needs the schemas and both scanners. The server runs as a non-root user
that needs the group owning the Docker socket: nothing to set on Docker
Desktop, `DOCKER_GID` on Linux (see `.env.example`).

## Running outside Docker

The server starts containers to check out and scan repositories, and those
containers need to see the checkout. Inside Docker that is a shared volume;
outside it, use a directory on the Docker host instead:

```sh
mvn -f pom.xml install -DskipTests
mvn -f ../../graphnous-java-scanner/pom.xml package -DskipTests

java -jar graphnous-api/target/graphnous-api-1.0-SNAPSHOT.jar \
  --graphnous.scanner.workspace.type=host \
  --graphnous.scanner.workspace.path=/absolute/path/to/checkouts \
  --graphnous.scanner.java-scanner=../../graphnous-java-scanner/cli/target/scanners/java-scanner.jar \
  --graphnous.scanner.typescript-scanner=../../graphnous-typescript-scanner/build/scanner.js
```

On Docker Desktop the checkout directory must be in a folder shared with
Docker (under your home directory, for example). If a server in Docker uses
the same Docker daemon, also pass `--graphnous.scanner.instance=<another name>`:
each server removes the containers of its own name on startup. Neo4j is expected at
`bolt://localhost:6687`; set `NEO4J_URI`, `NEO4J_USERNAME` and
`NEO4J_PASSWORD` for another instance.

## Configuration

Set in `graphnous-api/src/main/resources/application.yml`; override with
command-line arguments or environment variables.

| Property | Default | Meaning |
| --- | --- | --- |
| `graphnous.scans.timeout` | `1h` | An active scan without a status change for this long is marked failed. |
| `graphnous.scans.recovery-interval` | `1m` | How often to look for such scans. |
| `graphnous.scanner.instance` | `graphnous` | The name on the containers this server starts. On startup the server removes the containers of its name left by a previous run, so servers sharing a Docker daemon need different names. |
| `graphnous.scanner.workspace.type` | `volume` | `volume`: a named Docker volume the server has mounted (server in Docker). `host`: a directory on the Docker host (server outside Docker). |
| `graphnous.scanner.workspace.volume` | `graphnous-checkouts` | The volume, for type `volume`. |
| `graphnous.scanner.workspace.path` | `/checkouts` | Where the server sees the checkouts. |
| `graphnous.scanner.git-image` | `alpine/git:2.54.0` | The image checkouts run in. |
| `graphnous.scanner.java-scanner` | `scanners/java-scanner.jar` | The Java scanner copied into scanner containers. |
| `graphnous.scanner.typescript-scanner` | `scanners/scanner.js` | The TypeScript scanner copied into scanner containers. |
| `graphnous.security.enabled` (`GRAPHNOUS_SECURITY_ENABLED`) | `false` | Whether the API needs access tokens; see Security. |
| `graphnous.security.issuer-uri` (`GRAPHNOUS_PLATFORM_URL`) | `http://localhost:1338` | The Graphnous platform, which issues the access tokens. |
| `graphnous.security.default-organization-id` | `00000000-0000-0000-0000-000000000000` | The organization of every request with security off. |

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

## Tests

```sh
mvn test
```

Tests that need Docker (the Docker sandbox, git checkout and container
cancellation, and the Neo4j repository tests through Testcontainers) are
skipped when no Docker daemon is reachable. They run as the instance
`graphnous-test`, so they do not remove the containers of a server running on
the same daemon. Controller tests check requests and responses against the
OpenAPI spec.

## License

Apache License 2.0; see [LICENSE](LICENSE).
