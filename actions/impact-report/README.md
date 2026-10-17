# Graphnous impact report

A GitHub Action that comments on a pull request what its change affects:

- the HTTP calls, in any project of the system, that reached an endpoint
  on the base branch and reach none with the change;
- the classes, methods and fields it removes or changes that something
  uses, with where;
- the endpoints it removes and adds, and the projects affected.

Graphnous scans the pull request's head commit and compares it with the
newest completed scan of the base branch, so the base branch needs a
completed scan first. The job comments once, then edits that comment on
every new push.

```yaml
name: Impact report

on:
  pull_request:

permissions:
  contents: read
  pull-requests: write

jobs:
  impact:
    runs-on: ubuntu-latest
    steps:
      - uses: graphnous/graphnous-app/actions/impact-report@main
        with:
          graphnous-url: https://graphnous.example.com
          project-id: 6f1c0d2e-0000-0000-0000-000000000000
          token: ${{ secrets.GRAPHNOUS_TOKEN }}
```

The server must be reachable from GitHub's runners, or the job must run on
a self-hosted runner that reaches it.

## Inputs

| Input | Default | |
|---|---|---|
| `graphnous-url` | | The Graphnous server. |
| `project-id` | | The repository's project in Graphnous. |
| `token` | | An access token for the API, when the server's security is on. |
| `client-id`, `client-secret`, `platform-url` | | Instead of a token, an API key of the organization: the job gets a token for it at the platform. |
| `base-branch` | the pull request's base | The branch whose newest completed scan to compare with. |
| `scan-results` | | A directory of result files the job scanned itself, to upload instead of having the server scan. |
| `assess` | `false` | Head the comment with the server's impact agent's assessment, which ranks the risks. It needs an OpenAI key on the server and takes a while; the comment says when it could not be written. |
| `timeout-minutes` | `30` | How long to wait for the scan. |
| `github-token` | the job's token | The token to comment with. |

The scan's id is the output `scan-id`, and the report is in the job's
summary too.

## Scanning in the job

By default the server checks out the head commit and scans it, as a scan
started in the app does, so it needs access to the repository. For a pull
request from a fork, or a repository the server cannot reach, scan in the
job with `graphnous-scanner` (the `dev.graphnous:graphnous-scanner-cli`
package, which runs the scanner images in the runner's Docker) and upload
the results:

```yaml
      - uses: actions/checkout@v4
      - run: java -jar graphnous-scanner.jar . --output scan-results
      - uses: graphnous/graphnous-app/actions/impact-report@main
        with:
          graphnous-url: https://graphnous.example.com
          project-id: 6f1c0d2e-0000-0000-0000-000000000000
          token: ${{ secrets.GRAPHNOUS_TOKEN }}
          scan-results: scan-results
```

## Without the Action

The comment is `GET /api/v1/scans/{head}/pull-request-comment` on the server, in
Markdown, with the query parameters `baseBranch` (`main` by default),
`base` (a scan id, instead of the branch's newest completed scan) and
`assess`.
