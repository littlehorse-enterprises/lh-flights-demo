#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
docker compose --project-directory "$ROOT_DIR" down --volumes --remove-orphans
