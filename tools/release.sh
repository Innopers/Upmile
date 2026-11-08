#!/usr/bin/env bash
# 릴리스 APK를 빌드해 apk/INNOPERS_Upmile_release.apk 로 복사하고 서명자를 출력합니다.
set -euo pipefail
cd "$(dirname "$0")/.."

export ANDROID_HOME="${ANDROID_HOME:-/Volumes/DevSSD/Android/sdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/Volumes/DevSSD/gradle}"

./gradlew :app:assembleRelease
mkdir -p apk
OUT=apk/INNOPERS_Upmile_release.apk
cp app/build/outputs/apk/release/app-release.apk "$OUT"

APKSIGNER="$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1)"
echo "크기: $(du -h "$OUT" | cut -f1)"
"$APKSIGNER" verify --print-certs "$OUT" | grep -E "certificate DN|Verified"
