FROM gradle:9.6.1-jdk17
ARG CONNECTOR_REVISION
RUN git clone --quiet https://github.com/littlehorse-enterprises/lh-kafka-connect.git /src/connector \
    && git -C /src/connector checkout --quiet "$CONNECTOR_REVISION" \
    && /src/connector/gradlew -p /src/connector -Pversion=1.3.0 connector:buildConfluentBundle --no-daemon
ENTRYPOINT ["sh", "-c", "rm -rf /output/lh-kafka-connect && mkdir -p /output/lh-kafka-connect && cp -a /src/connector/connector/build/bundle/lh-kafka-connect/. /output/lh-kafka-connect/ && chown -R ${OUTPUT_UID:-0}:${OUTPUT_GID:-0} /output/lh-kafka-connect"]
