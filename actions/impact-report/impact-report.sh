#!/usr/bin/env bash
# Scans a pull request's head commit with Graphnous, or uploads the results
# the job scanned, waits for the scan, and comments its impact report on the
# pull request: as a new comment the first time, then by editing that one.
#
# Takes its inputs from the environment, as action.yml sets it; needs curl,
# jq and gh, which GitHub's runners have.
set -euo pipefail

: "${GRAPHNOUS_URL:?graphnous-url is required}"
: "${PROJECT_ID:?project-id is required}"
: "${PR_NUMBER:?the impact report runs on pull requests}"
: "${HEAD_BRANCH:?}" "${HEAD_SHA:?}" "${BASE_BRANCH:?}" "${GITHUB_REPOSITORY:?}"

MARKER="<!-- graphnous-impact -->"
api="${GRAPHNOUS_URL%/}/api/v1"

# An API key's token, from the platform, as a machine gets one
if [ -z "${GRAPHNOUS_TOKEN:-}" ] && [ -n "${CLIENT_ID:-}" ]; then
  : "${CLIENT_SECRET:?client-secret is required with client-id}" "${PLATFORM_URL:?platform-url is required with client-id}"
  GRAPHNOUS_TOKEN=$(curl -sS --fail-with-body -u "$CLIENT_ID:$CLIENT_SECRET" \
    -d grant_type=client_credentials "${PLATFORM_URL%/}/oauth2/token" | jq -r .access_token)
  echo "::add-mask::$GRAPHNOUS_TOKEN"
fi

auth=()
if [ -n "${GRAPHNOUS_TOKEN:-}" ]; then
  auth=(-H "Authorization: Bearer $GRAPHNOUS_TOKEN")
fi

# A request of the API: its body, or, when it fails, why on stderr
graphnous() {
  local body status
  body=$(mktemp)
  # The empty array is unbound to bash 3 under set -u
  if ! status=$(curl -sS --retry 3 -o "$body" -w '%{http_code}' ${auth[@]+"${auth[@]}"} "$@"); then
    echo "::error::Graphnous could not be reached at $GRAPHNOUS_URL" >&2
    return 1
  fi
  if [ "$status" -ge 400 ]; then
    echo "::error::Graphnous answered $status: $(cat "$body")" >&2
    return 1
  fi
  cat "$body"
}

# The head's scan
if [ -n "${SCAN_RESULTS:-}" ]; then
  files=()
  for file in "$SCAN_RESULTS"/*.json; do
    if [ -e "$file" ]; then
      files+=(-F "files=@$file;type=application/json")
    fi
  done
  if [ ${#files[@]} -eq 0 ]; then
    echo "::error::No result files in $SCAN_RESULTS"
    exit 1
  fi
  echo "Uploading $((${#files[@]} / 2)) result files of $HEAD_SHA"
  scan=$(graphnous -X POST "$api/projects/$PROJECT_ID/scan-results" \
    -F "branch=$HEAD_BRANCH" -F "revision=$HEAD_SHA" "${files[@]}")
else
  echo "Scanning $HEAD_SHA of $HEAD_BRANCH"
  scan=$(graphnous -X POST "$api/projects/$PROJECT_ID/scans" -H "Content-Type: application/json" \
    -d "$(jq -n --arg branch "$HEAD_BRANCH" --arg revision "$HEAD_SHA" '{branch: $branch, revision: $revision}')")
fi

scan_id=$(jq -r .id <<<"$scan")
echo "scan-id=$scan_id" >>"$GITHUB_OUTPUT"
echo "Scan $scan_id"

deadline=$((SECONDS + ${TIMEOUT_MINUTES:-30} * 60))
while :; do
  status=$(graphnous "$api/scans/$scan_id" | jq -r .status)
  case "$status" in
    COMPLETED) break ;;
    FAILED)
      echo "::error::Scan $scan_id failed; its steps in Graphnous say why"
      exit 1
      ;;
  esac
  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "::error::Scan $scan_id did not complete in ${TIMEOUT_MINUTES:-30} minutes; it is $status"
    exit 1
  fi
  sleep 10
done

# Where the branch started, to compare with what it was then rather than
# what the base branch got since; the newest scan of it otherwise
merge_base=""
if [ -n "${BASE_SHA:-}" ]; then
  merge_base=$(gh api "repos/$GITHUB_REPOSITORY/compare/$BASE_SHA...$HEAD_SHA" --jq .merge_base_commit.sha 2>/dev/null || true)
fi
base=()
if [ -n "$merge_base" ]; then
  base=(--data-urlencode "baseRevision=$merge_base")
fi

comment=$(mktemp)
graphnous -G "$api/scans/$scan_id/impact-comment" -H "Accept: text/markdown" \
  --data-urlencode "baseBranch=$BASE_BRANCH" --data-urlencode "assess=${ASSESS:-false}" ${base[@]+"${base[@]}"} >"$comment"

cat "$comment" >>"$GITHUB_STEP_SUMMARY"

# The comment of an earlier push, to edit
existing=$(gh api --paginate "repos/$GITHUB_REPOSITORY/issues/$PR_NUMBER/comments" \
  --jq ".[] | select(.body | startswith(\"$MARKER\")) | .id" | tail -n 1)

if [ -n "$existing" ]; then
  gh api -X PATCH "repos/$GITHUB_REPOSITORY/issues/comments/$existing" -F "body=@$comment" >/dev/null
  echo "Updated the impact report on #$PR_NUMBER"
else
  gh api -X POST "repos/$GITHUB_REPOSITORY/issues/$PR_NUMBER/comments" -F "body=@$comment" >/dev/null
  echo "Commented the impact report on #$PR_NUMBER"
fi
