#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
[[ ! -f .env.local ]] || { set -a; source .env.local; set +a; }
# Removing the demo volumes is explicit: ./cleanup.sh --reset
if [[ ${1:-} == --reset ]]; then docker compose down --volumes --remove-orphans; else docker compose down --remove-orphans; fi
