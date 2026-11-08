#!/usr/bin/env bash
# 릴리스 APK를 빌드해 apk/INNOPERS_Upmile_release.apk 로 복사하고 서명자를 출력합니다.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "ANDROID_HOME이 설정되지 않았습니다. Android SDK 경로를 지정하세요." >&2
  exit 1
fi
export ANDROID_HOME

./gradlew :app:assembleRelease
mkdir -p apk
OUT=apk/INNOPERS_Upmile_release.apk
cp app/build/outputs/apk/release/app-release.apk "$OUT"

APKSIGNER="$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1)"
echo "크기: $(du -h "$OUT" | cut -f1)"
CERTS="$("$APKSIGNER" verify --print-certs "$OUT")"
echo "$CERTS" | grep -E "certificate DN"
if echo "$CERTS" | grep -q "Android Debug"; then
  echo "릴리스 키가 없어 debug 키로 서명되었습니다. local.properties의 upmile.* 키를 확인하세요." >&2
  exit 1
fi
