# The Graphnous GitHub App

The Graphnous server can be a [GitHub App](https://docs.github.com/en/apps)
that reports on every pull request what its change affects:

- A pull request opened, pushed to or reopened scans its head commit, with
  a check run, `Graphnous impact: <project>`, on the commit while it runs.
- Once the scan is done, its impact report completes the check run and goes
  to the pull request as a comment. Every new push edits that comment
  instead of adding one.
- The report compares the pull request with the commit its branch started
  from, so what the base branch got since does not show as removed. The App
  scans that commit too when it has no scan of it, and reports once both
  scans are done. When GitHub cannot say where the branch started, or that
  scan fails, the report compares with the newest completed scan of the base
  branch, and the comment says so.
- A push to a repository's default branch scans it, so pull requests have a
  current scan to compare with, and most branches start from a commit that
  already has a scan.
- The server checks out the repositories the App is installed on with the
  App's own token, so it needs no SSH key for them.

The check run succeeds, or is neutral when calls no longer reach an
endpoint. It reports; it never blocks a merge.

There are two ways to create the App:

- [From Graphnous's settings](#create-it-from-graphnouss-settings), in a
  few clicks. Graphnous registers the App on GitHub and keeps its secrets,
  encrypted, in its database.
- [By hand](#create-it-by-hand), on GitHub, with its secrets in the
  server's configuration.

For a server on your own machine, which GitHub cannot reach, see
[Developing locally with smee.io](#developing-locally-with-smeeio).

## Before you start

- **GitHub must reach the server**, for the webhooks at
  `https://<server>/github/webhooks`, or a forwarder must (see
  [smee.io](#developing-locally-with-smeeio)).
- **The App must be on**: set `GRAPHNOUS_GITHUB_ENABLED=true` and restart
  the server.
- **Graphnous needs a project for each repository**, with the repository's
  git url, in any form (`https://github.com/acme/shop`,
  `git@github.com:acme/shop.git`). A repository with several projects, one
  per directory, gets a check run and a comment for each.

## Create it from Graphnous's settings

1. **Give the server a secrets key**, which encrypts the App's private key
   and webhook secret in the database:

   ```sh
   openssl rand -base64 32
   ```

   Set the output as `GRAPHNOUS_SECRETS_KEY` and restart the server. Keep
   the key: without it, the server cannot read the App's secrets, and the
   App has to be registered again.

2. **Tell the server where GitHub reaches it** (optional): set
   `GRAPHNOUS_GITHUB_SERVER_URL`, such as `https://graphnous.example.com`.
   Without it, the settings page asks for it.

3. **Open Settings in Graphnous.** Under **GitHub App**:
   - **Name**: the App's name on GitHub, which no other App on GitHub may
     have, such as `Graphnous Acme`.
   - **GitHub organization**: the organization to register it in, such as
     `acme`. Leave it empty to register it on your own account.
   - **Webhook URL**: leave it empty, unless GitHub delivers the webhooks
     somewhere other than the server, such as a smee.io channel.

4. **Click Create on GitHub.** GitHub shows the App with its permissions and
   events filled in. Click **Create GitHub App** there.

5. **Install it.** GitHub sends you back to Graphnous, which stores the App
   and takes you on to installing it. Pick the repositories to report on.

The settings page then shows the App as **Connected**. **Disconnect**
makes Graphnous forget it; the App stays registered on GitHub until you
delete it there, under the App's settings.

The page that GitHub sends you back to only works within an hour of
clicking **Create on GitHub**, and only once. If it says the setup is not
under way, start again from the settings page.

## Create it by hand

1. **Register the App** on GitHub: for your account at
   [github.com/settings/apps/new](https://github.com/settings/apps/new), or
   for an organization at
   `https://github.com/organizations/<organization>/settings/apps/new`.

   | Setting | Value |
   | --- | --- |
   | GitHub App name | Any name no other App has, such as `Graphnous Acme` |
   | Homepage URL | The server, such as `https://graphnous.example.com` |
   | Webhook: Active | On |
   | Webhook URL | `https://graphnous.example.com/github/webhooks` |
   | Webhook secret | A random string, such as `openssl rand -hex 32` gives |
   | Repository permissions | Checks: **Read and write**; Pull requests: **Read and write**; Contents: **Read-only**; Metadata: **Read-only** |
   | Subscribe to events | **Push**, **Pull request** |
   | Where can this GitHub App be installed? | **Only on this account** |

2. **Generate a private key**, at the bottom of the App's settings. GitHub
   downloads a `.pem` file.

3. **Configure the server** and restart it:

   ```sh
   GRAPHNOUS_GITHUB_ENABLED=true
   GRAPHNOUS_GITHUB_APP_ID=123456            # "App ID", at the top of the App's settings
   GRAPHNOUS_GITHUB_PRIVATE_KEY_FILE=/run/secrets/graphnous-github.pem
   GRAPHNOUS_GITHUB_WEBHOOK_SECRET=<the webhook secret>
   ```

   Or pass the key itself, its whole content, as
   `GRAPHNOUS_GITHUB_PRIVATE_KEY`.

4. **Install the App**: under **Install App** in its settings, on the
   repositories to report on.

An App the configuration names always wins over one set up from the
settings page; the page shows it as **From configuration**.

## Developing locally with smee.io

GitHub cannot deliver webhooks to `localhost`. [smee.io](https://smee.io)
receives them on a public channel and forwards them to your machine.

1. **Create a channel**: open [smee.io/new](https://smee.io/new) and copy
   its URL, such as `https://smee.io/aBcD1234efGH5678`.

2. **Forward it to the server**, and keep this running:

   ```sh
   npx smee-client --url https://smee.io/aBcD1234efGH5678 --target http://localhost:1337/github/webhooks
   ```

   The server's port is 1337, also when it runs in Docker Compose, which
   publishes it.

3. **Create the App** with the channel as its webhook URL:
   - From the settings page, as above, with **Server URL**
     `http://localhost:1337` and **Webhook URL** the channel. GitHub sends
     your browser back to `localhost`, which it can reach, and the webhooks
     to the channel. In the configuration, these are
     `GRAPHNOUS_GITHUB_SERVER_URL` and `GRAPHNOUS_GITHUB_WEBHOOK_URL`.
   - Or by hand, as above, with the channel as the **Webhook URL**.

4. **Install the App on a test repository**, and open a pull request. The
   forwarder prints each delivery, and the server answers it with `202`.

Anyone with a channel's URL can read what goes through it: the deliveries'
repositories, branches, commits and authors. Use a channel of your own, for
test repositories. No one can send the server deliveries of their own
through it, though: the server only takes deliveries signed with the App's
webhook secret.

The scans check the repositories out from GitHub with the App's token, so
the server on your machine needs to reach `github.com`, but nothing needs to
reach it except the forwarder.

## Configuration

| Property | Environment variable | Default | Meaning |
| --- | --- | --- | --- |
| `graphnous.github.enabled` | `GRAPHNOUS_GITHUB_ENABLED` | `false` | Whether the server is the App. |
| `graphnous.secrets.key` | `GRAPHNOUS_SECRETS_KEY` | none | 32 bytes in base64, which encrypt the secrets of an App set up from the settings page. |
| `graphnous.github.server-url` | `GRAPHNOUS_GITHUB_SERVER_URL` | none | Where GitHub, and your browser, reach the server, for an App set up from the settings page. |
| `graphnous.github.webhook-url` | `GRAPHNOUS_GITHUB_WEBHOOK_URL` | the server's `/github/webhooks` | Where GitHub delivers that App's webhooks, such as a smee.io channel. |
| `graphnous.github.app-id` | `GRAPHNOUS_GITHUB_APP_ID` | none | The App ID of an App created by hand. |
| `graphnous.github.private-key` | `GRAPHNOUS_GITHUB_PRIVATE_KEY` | none | Its private key, the content of its `.pem` file. |
| `graphnous.github.private-key-file` | `GRAPHNOUS_GITHUB_PRIVATE_KEY_FILE` | none | Or the path of that file. |
| `graphnous.github.webhook-secret` | `GRAPHNOUS_GITHUB_WEBHOOK_SECRET` | none | Its webhook secret. |
| `graphnous.github.api-url` | `GRAPHNOUS_GITHUB_API_URL` | `https://api.github.com` | GitHub's API, such as `https://github.example.com/api/v3` for GitHub Enterprise Server. |
| `graphnous.github.scan-default-branch` | `GRAPHNOUS_GITHUB_SCAN_DEFAULT_BRANCH` | `true` | Whether a push to a repository's default branch scans it. |
| `graphnous.github.assess` | `GRAPHNOUS_GITHUB_ASSESS` | `false` | Whether the impact agent heads each report with its assessment, which needs an OpenAI key. |
| `graphnous.github.sweep-interval` | | `PT1M` | How often the server looks for scans that are done but whose report a restart interrupted. |
| `graphnous.github.organization-id` | | `graphnous.security.default-organization-id` | The Graphnous organization the App works in, for every installation. |

## Troubleshooting

You can see what GitHub delivered, and what the server answered, in the
App's settings under **Advanced**, **Recent Deliveries**, and deliver one
again from there.

| What you see | Why, and what to do |
| --- | --- |
| Deliveries answered `503` | The server is not an App yet: create it, or configure it. |
| Deliveries answered `401` | The delivery is not signed with the webhook secret the server has. Check `GRAPHNOUS_GITHUB_WEBHOOK_SECRET`, or that the App set up from the settings page is the one GitHub delivers for. |
| Deliveries answered `202`, but no check run | No Graphnous project has the repository's git url. The server logs `No project is repository acme/shop`. |
| The check run says "No scan of main to compare with" | The commit the branch started from could not be scanned, and the base branch has no completed scan either. Push to it, which scans it, or scan it from Graphnous, then push to the pull request again. |
| The comment says it compares with the newest scan of `main` | The scan of the commit the branch started from failed, or GitHub could not say where the branch started; what `main` got since shows as removed. The scan's steps in Graphnous say why it failed. |
| The check run says "The scan failed" | The scan's steps in Graphnous say why, such as a checkout that failed. |
| Settings shows the App as **Not answering** | GitHub refused the App's key: it was revoked, or the App was deleted on GitHub. Generate a new key, or create the App again. |
| Settings asks for a secrets key | Set `GRAPHNOUS_SECRETS_KEY`, as in [step 1](#create-it-from-graphnouss-settings), and restart. |
