#!/usr/bin/env bash
# Download NASA SVS Deep Star Maps 2020.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT_DIR="$ROOT/frontend/public/starfield"
OUT_FILE="$OUT_DIR/starmap_2020_8k.exr"
URL="https://svs.gsfc.nasa.gov/vis/a000000/a004800/a004851/starmap_2020_8k.exr"

mkdir -p "$OUT_DIR"

if [[ -f "$OUT_FILE" ]]; then
  size="$(wc -c < "$OUT_FILE" | tr -d ' ')"
  # skip re-download if file exists.
  if [[ "$size" -gt 50000000 ]]; then
    echo "Already present: $OUT_FILE ($size bytes)"
    exit 0
  fi
fi

echo "Downloading $URL"
echo "  → $OUT_FILE"
curl -fL --progress-bar -o "$OUT_FILE.partial" "$URL"
mv "$OUT_FILE.partial" "$OUT_FILE"
echo "Done: $(wc -c < "$OUT_FILE" | tr -d ' ') bytes"
echo "See docs/ATTRIBUTION.md"
