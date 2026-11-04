FROM gradle:9.1-jdk21 AS build

WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000 \
    gradle quarkusBuild --no-daemon

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /deployments
COPY --from=build /workspace/build/quarkus-app/ .

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
