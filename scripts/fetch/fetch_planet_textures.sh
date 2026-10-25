#!/usr/bin/env bash
# Celestial body maps.
#
# https://visibleearth.nasa.gov/images/74218/december-blue-marble-next-generation
# https://www.solarsystemscope.com/textures/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT_DIR="$ROOT/frontend/public/starfield/bodies"
SSS="https://www.solarsystemscope.com/textures/download"
# Blue Marble Next Generation
BMNG_EARTH="https://eoimages.gsfc.nasa.gov/images/imagerecords/74000/74218/world.200412.3x5400x2700.jpg"

mkdir -p "$OUT_DIR"

download() {
  local url="$1"
  local out="$2"
  local force="${3:-0}"
  if [[ "$force" != "1" && -f "$out" ]]; then
    size="$(wc -c < "$out" | tr -d ' ')"
    if [[ "$size" -gt 10000 ]]; then
      echo "Already present: $out ($size bytes)"
      return 0
    fi
  fi
  echo "Downloading → $(basename "$out")"
  curl -fL --progress-bar -o "$out.partial" "$url"
  mv "$out.partial" "$out"
}

download "$BMNG_EARTH" "$OUT_DIR/earth_bluemarble.jpg" 1
rm -f "$OUT_DIR/earth_daymap.jpg"

sss_files=(
  "2k_sun.jpg:sun.jpg"
  "2k_mercury.jpg:mercury.jpg"
  "2k_venus_atmosphere.jpg:venus_atmosphere.jpg"
  "2k_moon.jpg:moon.jpg"
  "2k_mars.jpg:mars.jpg"
  "2k_jupiter.jpg:jupiter.jpg"
  "2k_saturn.jpg:saturn.jpg"
  "2k_saturn_ring_alpha.png:saturn_ring_alpha.png"
  "2k_uranus.jpg:uranus.jpg"
  "2k_neptune.jpg:neptune.jpg"
  "2k_earth_nightmap.jpg:earth_night_src.jpg"
)

for entry in "${sss_files[@]}"; do
  remote="${entry%%:*}"
  local_name="${entry##*:}"
  download "$SSS/$remote" "$OUT_DIR/$local_name" 0
done

python3 - "$OUT_DIR/earth_night_src.jpg" "$OUT_DIR/earth_night.png" << 'PY'
import sys
from PIL import Image
src, dst = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB")
px = im.load()
w, h = im.size
for y in range(h):
    for x in range(w):
        r, g, b = px[x, y]
        # Navy base is blue-dominant. City lights are white or warm, so they stay.
        if b > r and b > g:
            px[x, y] = (0, 0, 0)
im.save(dst)
print(f"Night lights: removed blue-dominant pixels → {dst}")
PY
rm -f "$OUT_DIR/earth_night.jpg"

echo "Done. See docs/ATTRIBUTION.md"
