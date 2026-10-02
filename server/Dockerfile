# Builds the Graphnous server with the scanners it hands to its scanner
# containers. Build from the repository root, as the server generates its
# API from graphnous-schemas:
#
#   docker build -f graphnous-app/server/Dockerfile .

FROM node:24-alpine AS typescript-scanner

WORKDIR /build/graphnous-typescript-scanner

COPY graphnous-typescript-scanner/package.json graphnous-typescript-scanner/package-lock.json ./
RUN npm ci

COPY graphnous-typescript-scanner/ .
RUN npm run build


FROM maven:3.9-eclipse-temurin-25 AS server

WORKDIR /build

COPY graphnous-schemas/ graphnous-schemas/
COPY graphnous-app/server/ graphnous-app/server/
COPY graphnous-java-scanner/ graphnous-java-scanner/

# The Java scanner builds on the server's scanner modules, so the server is
# installed first. The repository lives in the stage (no cache mount), so the
# second build sees what the first installed.
RUN mvn -B -q -f graphnous-app/server/pom.xml install -DskipTests \
 && mvn -B -q -f graphnous-java-scanner/pom.xml package -DskipTests


FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=server /build/graphnous-app/server/graphnous-api/target/graphnous-api-*.jar graphnous-server.jar
COPY --from=server /build/graphnous-java-scanner/cli/target/scanners/java-scanner.jar scanners/java-scanner.jar
COPY --from=typescript-scanner /build/graphnous-typescript-scanner/build/scanner.js scanners/scanner.js

# Repositories are checked out in the graphnous-checkouts volume, mounted here
# and, by name, in the git and scanner containers the server starts
ENV GRAPHNOUS_SCANNER_WORKSPACE_TYPE=volume \
    GRAPHNOUS_SCANNER_WORKSPACE_VOLUME=graphnous-checkouts \
    GRAPHNOUS_SCANNER_WORKSPACE_PATH=/checkouts
# The server only reads the checkouts; the git containers write them
RUN useradd --system --uid 10001 --no-create-home graphnous \
 && mkdir /checkouts \
 && chown graphnous /checkouts

# Needs the Docker socket's group to start containers; see docker-compose.yml
USER graphnous

EXPOSE 1337

ENTRYPOINT ["java", "-jar", "graphnous-server.jar"]
