#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
docker compose ps
curl -sf http://127.0.0.1:8080/api/health && echo || echo "health: unreachable"
