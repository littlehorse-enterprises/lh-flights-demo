#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "$ROOT_DIR"
# Select once; non-interactive runs default to the deterministic worker.
if [[ ! -f .env.local ]]; then
  choice=1
  if [[ -t 0 ]]; then
    printf 'Advisor: 1) rules (default)  2) OpenAI  3) Anthropic  4) Ollama Qwen 0.6B  5) Ollama Qwen 4B (~3 GB)\n'
    read -r -p 'Choose [1-5]: ' choice
  fi
  choice=${choice:-1}
  umask 077
  printf 'RULES_ADVISOR_ENABLED=true\nCOMPOSE_PROFILES=\n' > .env.local
  : > .agent.env
  case "$choice" in
    1) ;;
    2|3)
      read -r -s -p 'API key: ' api_key; printf '\n'
      [[ -n "$api_key" ]] || { printf 'An API key is required.\n' >&2; rm .env.local; exit 1; }
      [[ "$api_key" =~ ^[A-Za-z0-9_-]+$ ]] || { printf 'Unexpected characters in API key.\n' >&2; rm .env.local; exit 1; }
      if [[ "$choice" == 2 ]]; then provider=openai; default_model=gpt-4.1-mini; else provider=anthropic; default_model=claude-sonnet-4-5-20250929; fi
      read -r -p "Model [$default_model]: " model
      model=${model:-$default_model}
      [[ "$model" =~ ^[A-Za-z0-9._:/-]+$ ]] || { printf 'Invalid model name.\n' >&2; rm .env.local; exit 1; }
      printf 'AGENT_CHAT_MODEL_PROVIDER=%s\nAGENT_CHAT_MODEL_%s_API_KEY=%s\nAGENT_CHAT_MODEL_%s_MODEL=%s\n' "$provider" "${provider^^}" "$api_key" "${provider^^}" "$model" > .agent.env
      printf 'RULES_ADVISOR_ENABLED=false\nCOMPOSE_PROFILES=model\n' > .env.local
      unset api_key
      ;;
    4|5)
      if [[ "$choice" == 4 ]]; then model=qwen3:0.6b; else model=qwen3:4b; fi
      printf 'AGENT_CHAT_MODEL_PROVIDER=openai\nAGENT_CHAT_MODEL_OPENAI_API_KEY=ollama\nAGENT_CHAT_MODEL_OPENAI_BASE_URL=http://ollama:11434/v1\nAGENT_CHAT_MODEL_OPENAI_MODEL=%s\n' "$model" > .agent.env
      printf 'RULES_ADVISOR_ENABLED=false\nCOMPOSE_PROFILES=model,ollama\nOLLAMA_MODEL=%s\n' "$model" > .env.local
      ;;
    *) printf 'Choose a value from 1 to 5.\n' >&2; rm .env.local; exit 1;;
  esac
fi
[[ -f .agent.env ]] || touch .agent.env
set -a
source .env
source .env.local
set +a
PLUGIN_DIR="$ROOT_DIR/plugins/lh-kafka-connect"
EXPECTED_BUILD="$CONNECTOR_REVISION-lh1.3.0"
if [[ ! -f "$PLUGIN_DIR/.build-version" ]] || [[ "$(cat "$PLUGIN_DIR/.build-version")" != "$EXPECTED_BUILD" ]]; then
  docker build --build-arg "CONNECTOR_REVISION=$CONNECTOR_REVISION" --file connector-builder.Dockerfile --tag connection-rescue-connect-builder .
  mkdir -p plugins
  docker run --rm --volume "$ROOT_DIR/plugins:/output" connection-rescue-connect-builder
  printf '%s\n' "$EXPECTED_BUILD" > "$PLUGIN_DIR/.build-version"
fi
if [[ ${COMPOSE_PROFILES:-} == *ollama* ]]; then
  docker compose up -d --wait ollama
  docker compose exec -T ollama ollama pull "$OLLAMA_MODEL"
fi
docker compose up --build -d --wait
./configure-connectors.sh
./seed.sh
printf '\nConnection Rescue: http://localhost:5173/backoffice\nLittleHorse: http://localhost:3000\nStart traffic: python3 simulator/simulate.py\n'
