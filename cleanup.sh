#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
[[ ! -f .env.local ]] || { set -a; source .env.local; set +a; }
docker compose --project-name connection-rescue down --volumes --remove-orphans --rmi all
docker image rm connection-rescue-connect-builder >/dev/null 2>&1 || true
rm -rf plugins/lh-kafka-connect
rm -f .env.local .agent.env
