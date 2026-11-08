#!/usr/bin/env bash
# Upmile 실제 신호 시연 스크립트: Pleos 에뮬레이터 Virtual Console(8088)과 adb로 차량 신호를 흉내 냅니다.
set -euo pipefail

CONSOLE=http://127.0.0.1:8088
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCENARIO_DIR="$ROOT/assets/scenarios"

usage() {
  cat <<'U'
사용법: tools/demo.sh <명령>

  setup [--fix-location]  포트 연결·하트비트·초기 상태(주행거리 300km, 착석, 벨트 경고 꺼짐, P단)
                          --fix-location: 멈춘 위치 서비스를 강제 종료해 되살림
  belt on|off             SeatBelt 경고등 켜기/끄기
  drive [commute]         시나리오를 실시간(약 95초)으로 재생하고 끝에 P단으로 둠
  reset                   P단·속도 0·와이퍼 끔·벨트 경고 끔·문 닫힘, 하트비트 중단
  stop                    setup이 띄운 하트비트만 중단

시연 순서
  1. tools/demo.sh setup            (콘솔 페이지를 열어 두면 무드조명 변화가 보입니다)
  2. 앱에서 루틴 → 점검 (tools/demo.sh belt on / off 로 체크 항목 변화 확인) → 미리보기
     → 출발 화면의 "회사로 안내 시작"
  3. tools/demo.sh drive commute
  4. 도착해 P단이 되면 앱이 기록 화면을 준비합니다. 기본 내비게이션이 앞에 있으면
     하단 앱 아이콘으로 Upmile로 돌아오면 기록 화면이 보입니다
  5. tools/demo.sh reset            (하트비트도 함께 중단)
U
}

if [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
  ADB="$ANDROID_HOME/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then
  ADB="$(command -v adb)"
else
  echo "adb를 찾을 수 없습니다. ANDROID_HOME을 설정하거나 PATH에 adb를 추가하세요." >&2
  exit 1
fi

put() { curl -fsS -X PUT "$CONSOLE$1" >/dev/null || { echo "콘솔 쓰기 실패: $1 (setup을 먼저 실행했나요?)" >&2; return 1; }; }
beat() { curl -fsS -X POST "$CONSOLE/browser/session?client=upmile-demo" >/dev/null 2>&1 || true; }

HB_FILE="${TMPDIR:-/tmp}/upmile-demo-hb.pid"
stop_setup_heartbeat() {
  if [[ -f "$HB_FILE" ]]; then
    kill "$(cat "$HB_FILE")" 2>/dev/null || true
    rm -f "$HB_FILE"
  fi
}

HB_PID=""
start_heartbeat() {
  beat
  ( while true; do sleep 1; beat; done ) &
  HB_PID=$!
  trap '[[ -n "$HB_PID" ]] && kill "$HB_PID" 2>/dev/null || true' EXIT
  trap 'exit 130' INT TERM
}

neutral() {
  put "/vehicle/gear?gear=Park"
  put "/vehicle/controls?speed_kph=0&accelerator_pct=0&brake=0&steering=0"
  put "/vehicle/body?front_wiper=Off"
  put "/vehicle/body?door_fl=false"
  put "/vehicle/telltales/simulator?type=SeatBelt&state=Off&color=Red"
}

cmd_setup() {
  local fix=0
  [[ "${1:-}" == "--fix-location" ]] && fix=1
  "$ADB" forward tcp:8088 tcp:8088 >/dev/null
  # 하트비트가 없으면 콘솔 쓰기가 503으로 거부되므로 setup 뒤에도 남겨 둔다.
  stop_setup_heartbeat
  nohup bash -c "while true; do curl -fsS -X POST '$CONSOLE/browser/session?client=upmile-demo' >/dev/null 2>&1; sleep 1; done" >/dev/null 2>&1 &
  echo $! > "$HB_FILE"
  echo "하트비트 시작 (pid $!, 끝낼 때: tools/demo.sh stop 또는 reset)"
  sleep 1
  put "/vehicle/odometer?remaining_range_m=300000"
  put "/vehicle/seat?kind=occupied&area=driver&value=true"
  neutral
  if (( fix )); then
    local u; u="$("$ADB" shell am get-current-user | tr -d '\r')"
    "$ADB" shell am force-stop --user "$u" ai.pleos.playground.service.location.fused || true
    echo "위치 서비스 재시작"
  fi
  echo "setup 완료"
}

cmd_belt() {
  case "${1:-}" in
    on)  start_heartbeat; put "/vehicle/telltales/simulator?type=SeatBelt&state=On&color=Red" ;;
    off) start_heartbeat; put "/vehicle/telltales/simulator?type=SeatBelt&state=Off&color=Red" ;;
    *) usage; exit 1 ;;
  esac
}

cmd_reset() { start_heartbeat; neutral; stop_setup_heartbeat; echo "reset 완료"; }

cmd_drive() {
  local file="$SCENARIO_DIR/${1:-commute}_drive.json"
  [[ -f "$file" ]] || { echo "시나리오 없음: $file" >&2; exit 1; }
  "$ADB" forward tcp:8088 tcp:8088 >/dev/null
  start_heartbeat
  sleep 1
  put "/vehicle/gear?gear=Park"
  # python3 -I: 현재 디렉터리의 모듈을 읽지 않는다. 프레임 시각(t)에 맞춰 실시간 재생.
  ADB="$ADB" CONSOLE="$CONSOLE" python3 -I - "$file" <<'PY'
import json, signal, os, subprocess, sys, time, urllib.request, urllib.error

# 백그라운드 실행이나 부모 셸이 SIGINT를 무시로 넘겨도 Ctrl-C/종료 신호로 정리 단계가 돌게 한다.
signal.signal(signal.SIGINT, signal.default_int_handler)
signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(KeyboardInterrupt()))

adb, console = os.environ["ADB"], os.environ["CONSOLE"]
frames = json.load(open(sys.argv[1]))
GEAR = {"P": "Park", "D": "Drive", "R": "Reverse", "N": "Neutral"}

def put(path):
    try:
        urllib.request.urlopen(urllib.request.Request(console + path, method="PUT"), timeout=3).read()
    except (urllib.error.URLError, OSError) as e:
        print("콘솔 쓰기 실패 %s: %s" % (path, e), file=sys.stderr)

def fix(f):
    subprocess.run([adb, "emu", "geo", "fix", str(f["lon"]), str(f["lat"])],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

try:
    fix(frames[0])
    print("출발 위치 설정, 5초 대기")
    time.sleep(5)  # 이전 위치가 첫 D 샘플에 남으면 출발점이 틀어져 도착이 감지되지 않는다.
    gear = "P"
    wiper = False
    t0 = time.monotonic()
    for f in frames:
        delay = f["t"] / 1000.0 - (time.monotonic() - t0)
        if delay > 0:
            time.sleep(delay)
        fix(f)
        if f["gear"] != gear:
            if f["gear"] not in GEAR:
                continue
            gear = f["gear"]
            put("/vehicle/gear?gear=" + GEAR[gear])
        if f.get("wiperOn") and not wiper:
            wiper = True
            put("/vehicle/body?front_wiper=Auto")
        put("/vehicle/controls?speed_kph=%d&accelerator_pct=%d&brake=%d&steering=%d" % (
            round(f["speedMps"] * 3.6), 0 if f["brakePressed"] else (20 if f["speedMps"] > 0 else 0),
            80 if f["brakePressed"] else 0, round(f["steeringDeg"])))
        if int(f["t"] / 1000) % 10 == 0 and f["t"] % 1000 < 200:
            print("t=%ds %s %d km/h" % (f["t"] // 1000, gear, round(f["speedMps"] * 3.6)))
except KeyboardInterrupt:
    print("중단됨", file=sys.stderr)
    interrupted = True
else:
    interrupted = False
finally:
    # 중단·오류여도 D단·주행 속도로 남지 않게 정차 상태로 돌린다.
    put("/vehicle/gear?gear=Park")
    put("/vehicle/controls?speed_kph=0&accelerator_pct=0&brake=0&steering=0")
print("재생 끝 (P단)")
sys.exit(130 if interrupted else 0)
PY
}

case "${1:-}" in
  setup) shift; cmd_setup "$@" ;;
  belt) shift; cmd_belt "$@" ;;
  drive) shift; cmd_drive "$@" ;;
  reset) cmd_reset ;;
  stop) stop_setup_heartbeat; echo "하트비트 중단" ;;
  *) usage; [[ -z "${1:-}" || "${1:-}" == -h || "${1:-}" == --help ]] || exit 1 ;;
esac
