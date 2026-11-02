#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
source "$ROOT_DIR/.env"

PLUGIN_DIR="$ROOT_DIR/plugins/lh-kafka-connect"
if [[ ! -f "$PLUGIN_DIR/manifest.json" ]]; then
  docker build \
    --build-arg "CONNECTOR_REVISION=$CONNECTOR_REVISION" \
    --file "$ROOT_DIR/connector-builder.Dockerfile" \
    --tag lh-demo-motive-connector-builder \
    "$ROOT_DIR"
  mkdir -p "$ROOT_DIR/plugins"
  docker run --rm \
    --volume "$ROOT_DIR/plugins:/output" \
    lh-demo-motive-connector-builder
fi

docker compose --project-directory "$ROOT_DIR" up --build -d --wait

topics=(truck-locations pickups overfilled-cans service-exceptions no-service-events customer-responses)
for topic in "${topics[@]}"; do
  docker compose --project-directory "$ROOT_DIR" exec -T kafka \
    /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists \
    --topic "$topic" --partitions 6 --replication-factor 1
done

CONNECT_API=http://localhost:8083 "$ROOT_DIR/configure-connectors.sh"

printf 'Demo ready. Operations control room: http://localhost:5173\n'
printf 'LittleHorse dashboard: http://localhost:3000\n'
