#!/usr/bin/env bash
# usage: tools/fetch_route.sh <id> <startLat> <startLon> <endLat> <endLon>
# e.g.   tools/fetch_route.sh commute 37.4979 127.0276 37.3948 127.1112
# Reads tmap.appKey from local.properties (never commit it); saves assets/routes/<id>.json
set -euo pipefail
cd "$(dirname "$0")/.."
[ $# -eq 5 ] || { echo "usage: $0 <id> <startLat> <startLon> <endLat> <endLon>" >&2; exit 1; }
KEY=$(grep '^tmap.appKey=' local.properties | cut -d= -f2-)
[ -n "$KEY" ] || { echo "tmap.appKey missing in local.properties" >&2; exit 1; }
curl -sf -X POST "https://apis.openapi.sk.com/tmap/routes?version=1" \
  -H "appKey: $KEY" -H "Content-Type: application/json" \
  -d "{\"startX\":$3,\"startY\":$2,\"endX\":$5,\"endY\":$4,\"reqCoordType\":\"WGS84GEO\",\"resCoordType\":\"WGS84GEO\"}" \
  -o "assets/routes/$1.json"
echo "saved assets/routes/$1.json"
