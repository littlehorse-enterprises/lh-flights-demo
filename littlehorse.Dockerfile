ARG LITTLEHORSE_VERSION=1.2.0

FROM ghcr.io/littlehorse-enterprises/littlehorse/lhctl:${LITTLEHORSE_VERSION} AS lhctl
FROM ghcr.io/littlehorse-enterprises/littlehorse/lh-server:${LITTLEHORSE_VERSION}

COPY --from=lhctl /usr/local/bin/lhctl /usr/local/bin/lhctl
