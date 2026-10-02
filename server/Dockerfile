# Builds the Graphnous server with the scanners it hands to its scanner
# containers. Build from the repository root, as the server generates its
# API from graphnous-schemas, after downloading the scanners into
# server/scanners (see download-scanners.sh):
#
#   graphnous-app/server/download-scanners.sh
#   docker build -f graphnous-app/server/Dockerfile .

FROM maven:3.9-eclipse-temurin-25 AS server

WORKDIR /build

COPY graphnous-schemas/ graphnous-schemas/
COPY graphnous-app/server/ graphnous-app/server/

RUN mvn -B -q -f graphnous-app/server/pom.xml package -DskipTests


FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=server /build/graphnous-app/server/graphnous-api/target/graphnous-api-*.jar graphnous-server.jar
COPY graphnous-app/server/scanners/java-scanner.jar graphnous-app/server/scanners/scanner.js scanners/

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
