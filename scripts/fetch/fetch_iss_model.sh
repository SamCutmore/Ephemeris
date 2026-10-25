#!/usr/bin/env bash
# NASA VTA ISS model.
# https://science.nasa.gov/resource/international-space-station-3d-model/
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
dest="$root/frontend/public/starfield/iss"
mkdir -p "$dest"
curl -fL --retry 3 -o "$dest/ISS_stationary.glb" \
  "https://assets.science.nasa.gov/content/dam/science/psd/solar/2023/09/i/ISS_stationary.glb"
echo "Wrote $dest/ISS_stationary.glb"
