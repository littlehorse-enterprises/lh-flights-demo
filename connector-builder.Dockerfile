FROM gradle:9.6.1-jdk17

ARG CONNECTOR_REVISION

RUN apt-get update \
    && apt-get install --yes --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/* \
    && test -n "$CONNECTOR_REVISION" \
    && git clone --quiet https://github.com/littlehorse-enterprises/lh-kafka-connect.git /src/lh-kafka-connect \
    && git -C /src/lh-kafka-connect checkout --quiet "$CONNECTOR_REVISION" \
    && /src/lh-kafka-connect/gradlew -p /src/lh-kafka-connect connector:buildConfluentBundle

ENTRYPOINT ["sh", "-c", "rm -rf /output/lh-kafka-connect && mkdir -p /output/lh-kafka-connect && cp -a /src/lh-kafka-connect/connector/build/bundle/lh-kafka-connect/. /output/lh-kafka-connect/"]
