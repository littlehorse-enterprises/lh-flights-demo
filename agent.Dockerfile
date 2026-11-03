FROM gradle:9.1-jdk25 AS build
ARG AGENT_REVISION
RUN git clone --quiet https://github.com/littlehorse-enterprises/lh-agent-connector.git /src/agent \
    && git -C /src/agent checkout --quiet "$AGENT_REVISION" \
    && gradle -p /src/agent -Pversion=1.3.0 quarkusBuild --no-daemon
FROM eclipse-temurin:25-jre
WORKDIR /deployments
COPY --from=build /src/agent/build/quarkus-app/ ./
ENTRYPOINT ["java","-jar","quarkus-run.jar"]
