#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${ROOT}/.env"

prompt() {
  local var="$1" default="${2:-}" silent="${3:-}"
  local val
  if [[ -n "$default" ]]; then
    prompt_text="$var [$default]: "
  else
    prompt_text="$var: "
  fi
  if [[ "$silent" == "1" ]]; then
    read -r -s -p "$prompt_text" val
    echo
  else
    read -r -p "$prompt_text" val
  fi
  if [[ -z "$val" ]]; then
    val="$default"
  fi
  printf '%s=%s\n' "$var" "$val"
}

{
  prompt NEXUS_REGISTRY
  prompt NEXUS_USERNAME
  prompt NEXUS_PASSWORD "" 1
  prompt IMAGE_TAG
  prompt SPRING_DATASOURCE_URL
  prompt SPRING_DATASOURCE_USERNAME
  prompt SPRING_DATASOURCE_PASSWORD "" 1
  prompt NEGATIVEONE_INGEST_TOKEN "" 1
} >"$ENV_FILE"

chmod 600 "$ENV_FILE"
echo "Wrote $ENV_FILE"
