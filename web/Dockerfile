# Builds the Graphnous web app. Build from this folder:
#
#   docker build -t graphnous-web .
#
# The build generates the API client, which downloads the OpenAPI spec. Run
# the image with GRAPHNOUS_API_URL, where the web app's server reaches the
# API, such as http://server:1337 on a compose network.

FROM node:24-bookworm-slim AS web

# OpenAPI Generator, which generates the API client, runs on Java
COPY --from=eclipse-temurin:25-jre /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:$PATH \
    NEXT_TELEMETRY_DISABLED=1 \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
# public holds what the app serves as it is, and may be empty or missing
RUN mkdir -p public && npm run build


FROM node:24-bookworm-slim

WORKDIR /app

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# The standalone server, with the static files it serves itself
COPY --from=web --chown=node /build/.next/standalone ./
COPY --from=web --chown=node /build/.next/static ./.next/static
COPY --from=web --chown=node /build/public ./public

USER node

EXPOSE 3000

CMD ["node", "server.js"]
