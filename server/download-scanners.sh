#!/bin/sh
# Downloads the scanners the server runs into scanners/ next to this script,
# from the releases of graphnous-java-scanner and graphnous-typescript-scanner
# at the versions below. Both repositories are private, so gh needs a token
# that can read them (gh auth login, or GH_TOKEN).

set -eu

JAVA_SCANNER_VERSION="${JAVA_SCANNER_VERSION:-0.1.0}"
TYPESCRIPT_SCANNER_VERSION="${TYPESCRIPT_SCANNER_VERSION:-0.1.0}"

scanners="$(dirname "$0")/scanners"

gh release download "v$JAVA_SCANNER_VERSION" \
    --repo graphnous/Graphnous-java-scanner \
    --pattern java-scanner.jar \
    --dir "$scanners" \
    --clobber

gh release download "v$TYPESCRIPT_SCANNER_VERSION" \
    --repo graphnous/Graphnous-typescript-scanner \
    --pattern scanner.js \
    --dir "$scanners" \
    --clobber

echo "Downloaded the Java scanner $JAVA_SCANNER_VERSION and the TypeScript scanner $TYPESCRIPT_SCANNER_VERSION into $scanners"
