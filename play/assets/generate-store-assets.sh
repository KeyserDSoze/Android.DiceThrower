#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/play/generated"
ICON_SRC="$ROOT/src/app/src/main/res/drawable-nodpi/ic_launcher_art.png"
FEATURE_SRC="$ROOT/play/assets/feature-graphic.svg"

mkdir -p "$OUT"

if command -v magick >/dev/null 2>&1; then
  CONVERT=(magick)
elif command -v convert >/dev/null 2>&1; then
  CONVERT=(convert)
else
  echo "ImageMagick is required (magick or convert)." >&2
  exit 1
fi

"${CONVERT[@]}" "$ICON_SRC" -resize 512x512 -strip "$OUT/icon-512.png"
"${CONVERT[@]}" -background none "$FEATURE_SRC" -resize 1024x500! -strip "$OUT/feature-graphic-1024x500.png"

cat <<EOF
Generated:
  $OUT/icon-512.png
  $OUT/feature-graphic-1024x500.png
EOF
