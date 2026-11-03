#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
for kind in passengers flights; do
  docker compose --project-directory "$ROOT_DIR" exec -T backend curl --fail --silent --show-error \
    -H 'Content-Type: application/json' --data-binary @- "http://localhost:8081/api/$kind" < "$ROOT_DIR/fixtures/$kind.json"
done
printf 'Schedules and passengers loaded.\n'
