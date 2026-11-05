#!/usr/bin/env bash
# Builds assets/regions/sigungu.geojson (시·군·구 polygons, props: sgg = 5-digit code, sggnm = name).
# Source: vuski/admdongkor (통계청 SGIS 기반, CC BY 4.0). Needs node/npx. Usage: tools/build_regions.sh [ver]
set -euo pipefail
VER="${1:-ver20261001}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${WORK_DIR:-/private/tmp/upmile-regions}"
mkdir -p "$WORK" "$ROOT/assets/regions"
IN="$WORK/HangJeongDong_$VER.geojson"
[ -f "$IN" ] || curl -fL -o "$IN" "https://raw.githubusercontent.com/vuski/admdongkor/master/$VER/HangJeongDong_$VER.geojson"
cd "$WORK"
npx --yes mapshaper "$IN" -each 'sgg=adm_cd2.substr(0,5)' -dissolve sgg copy-fields=sggnm \
  -simplify 3% keep-shapes -o "$ROOT/assets/regions/sigungu.geojson" format=geojson
ls -l "$ROOT/assets/regions/sigungu.geojson"
