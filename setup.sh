#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "$ROOT_DIR"
command -v docker >/dev/null || { printf 'Install Docker Desktop or Docker Engine with Compose first.\n' >&2; exit 1; }
docker info >/dev/null 2>&1 || { printf 'Docker is not running or is inaccessible. Start Docker and retry ./setup.sh.\n' >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { printf 'Docker Compose v2.30+ is required. Update Docker and retry.\n' >&2; exit 1; }
# Select on every run; non-interactive runs default to the deterministic worker.
configure_advisor() {
  choice=1
  if [[ -t 0 ]]; then
    printf 'Advisor: 1) rules (default)  2) OpenAI  3) Anthropic  4) Ollama Qwen 0.6B  5) Ollama Qwen 4B (~3 GB)\n'
    read -r -p 'Choose [1-5]: ' choice
  fi
  choice=${choice:-1}
  umask 077
  rules=true
  profiles=
  agent_config=
  ollama_config=
  case "$choice" in
    1) ;;
    2|3)
      read -r -s -p 'API key: ' api_key; printf '\n'
      [[ -n "$api_key" ]] || { printf 'An API key is required.\n' >&2; exit 1; }
      [[ "$api_key" =~ ^[A-Za-z0-9_-]+$ ]] || { printf 'Unexpected characters in API key.\n' >&2; exit 1; }
      if [[ "$choice" == 2 ]]; then provider=openai; provider_key=OPENAI; default_model=gpt-4.1-mini; else provider=anthropic; provider_key=ANTHROPIC; default_model=claude-sonnet-4-5-20250929; fi
      read -r -p "Model [$default_model]: " model
      model=${model:-$default_model}
      [[ "$model" =~ ^[A-Za-z0-9._:/-]+$ ]] || { printf 'Invalid model name.\n' >&2; exit 1; }
      agent_config=$(printf 'AGENT_CHAT_MODEL_PROVIDER=%s\nAGENT_CHAT_MODEL_%s_API_KEY=%s\nAGENT_CHAT_MODEL_%s_MODEL=%s\n' "$provider" "$provider_key" "$api_key" "$provider_key" "$model")
      rules=false
      profiles=model
      unset api_key
      ;;
    4|5)
      if [[ "$choice" == 4 ]]; then model=qwen3:0.6b; else model=qwen3:4b; fi
      agent_config=$(printf 'AGENT_CHAT_MODEL_PROVIDER=openai\nAGENT_CHAT_MODEL_OPENAI_API_KEY=ollama\nAGENT_CHAT_MODEL_OPENAI_BASE_URL=http://ollama:11434/v1\nAGENT_CHAT_MODEL_OPENAI_MODEL=%s\n' "$model")
      rules=false
      profiles=model,ollama
      ollama_config="OLLAMA_MODEL=$model"
      ;;
    *) printf 'Choose a value from 1 to 5.\n' >&2; exit 1;;
  esac
  printf '%s\n' "$agent_config" > .agent.env
  printf 'RULES_ADVISOR_ENABLED=%s\nCOMPOSE_PROFILES=%s\n%s\n' "$rules" "$profiles" "$ollama_config" > .env.local
  unset agent_config
}
configure_advisor
[[ -f .agent.env ]] || touch .agent.env
set -a
source .env
source .env.local
set +a
PLUGIN_DIR="$ROOT_DIR/plugins/lh-kafka-connect"
EXPECTED_BUILD="$CONNECTOR_REVISION-lh1.3.0"
if [[ ! -f "$PLUGIN_DIR/.build-version" ]] || [[ "$(cat "$PLUGIN_DIR/.build-version")" != "$EXPECTED_BUILD" ]]; then
  printf '\nBuilding the LittleHorse Kafka connector (first run may take several minutes)…\n'
  docker build --network "${DOCKER_BUILD_NETWORK:-default}" --build-arg "CONNECTOR_REVISION=$CONNECTOR_REVISION" --file connector-builder.Dockerfile --tag connection-rescue-connect-builder .
  mkdir -p plugins
  docker run --rm --env "OUTPUT_UID=$(id -u)" --env "OUTPUT_GID=$(id -g)" --volume "$ROOT_DIR/plugins:/output" connection-rescue-connect-builder
  printf '%s\n' "$EXPECTED_BUILD" > "$PLUGIN_DIR/.build-version"
fi
# Stop the previous advisor, including services outside the newly selected profiles.
docker compose --profile model --profile ollama stop agent ollama
if [[ ${COMPOSE_PROFILES:-} == *ollama* ]]; then
  docker compose up -d --wait ollama
  docker compose exec -T ollama ollama pull "$OLLAMA_MODEL"
fi
printf '\nBuilding and starting Connection Rescue…\n'
docker compose up --build -d --wait
printf '\nConfiguring Kafka Connect and loading flight schedules…\n'
./configure-connectors.sh
./seed.sh
printf '\nConnection Rescue: http://localhost:5173/backoffice\nLittleHorse: http://localhost:3000\nStart traffic: python3 simulator/simulate.py\n'
