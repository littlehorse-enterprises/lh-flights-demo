#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
CONNECT_CONTAINER=$(docker compose --project-directory "$ROOT_DIR" ps -q kafka-connect)
CONNECT_NETWORK=$(docker inspect --format '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}}{{end}}' "$CONNECT_CONTAINER")

for config in "$ROOT_DIR"/connectors/*.json; do
  name=$(basename "$config" .json)
  docker run --rm --network "$CONNECT_NETWORK" \
    --volume "$ROOT_DIR/connectors:/connectors:ro" \
    curlimages/curl:8.12.1 \
    --fail --silent --show-error -X PUT \
    -H 'Content-Type: application/json' \
    --data-binary "@/connectors/$name.json" \
    "http://kafka-connect:8083/connectors/$name/config" >/dev/null
  printf 'Configured %s\n' "$name"
done
