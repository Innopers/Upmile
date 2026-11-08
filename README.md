# Upmile

운전 중에 가르치지 않습니다. 출발 전 몇 분, 오늘 길의 가장 어려운 곳을 차 안에서 먼저 달려 봅니다.

Upmile은 초보 운전자를 위한 Pleos Connect(Android Automotive) 앱입니다.
출발 전 호흡 루틴(차량 무드조명·공조 연동)과 차량 점검, 어려운 구간을 미리 달려 보는 길 미리보기를 거쳐 출발합니다.
주행 중에는 화면을 숨기고 음성으로만 안내하며, 도착하면 오늘의 기록을 남깁니다.

## 실행 방법

1. Pleos 에뮬레이터를 콜드 부팅합니다.
2. APK를 설치합니다(`-g`는 런타임 권한을 함께 허용합니다).

   ```bash
   adb install -r -g apk/INNOPERS_Upmile_release.apk
   ```

   `-g`를 쓰지 않았다면 현재 사용자에게 권한을 직접 허용합니다.

   ```bash
   adb shell pm grant --user $(adb shell am get-current-user) app.upmile android.car.permission.CAR_SPEED
   adb shell pm grant --user $(adb shell am get-current-user) app.upmile pleos.car.permission.FUSED_LOCATION
   ```

3. 앱을 실행합니다. **가장 쉬운 방법**은 화면 위쪽의 **체험 모드** 버튼입니다. 콘솔 없이, 기록해 둔 약 95초의 출퇴근 주행을 음성과 함께 재생하고 기록 화면으로 끝납니다. 출발 탭에서 "체험 주행 시작"을 누르세요.
4. 실제 신호 모드: Virtual Console(8088 포트)을 `adb forward tcp:8088 tcp:8088`로 연결한 뒤 `tools/demo.sh`로 차량 신호와 위치를 재생합니다(아래 시연 방법).

## 테스트 환경

- Pleos Connect Emulator 2.1.0 / Android 14 (API 34), arm64 이미지, Apple Silicon Mac
- Virtual Console 사용
- SDK: Vehicle 2.3.2, NaviHelper 3.0.5, FusedLocation 1.1.3, TextToSpeech 3.3.1
- APK에는 arm64-v8a와 x86_64 네이티브 라이브러리가 모두 들어 있지만, **검증은 arm64 에뮬레이터에서만 했습니다.** x86_64(Windows) 환경은 구하지 못해 확인하지 못했습니다.

## APK 위치

`apk/INNOPERS_Upmile_release.apk`

릴리스 서명 키가 없는 환경에서 소스로 빌드하면 debug 키로 서명됩니다. 직접 빌드는 `tools/release.sh`입니다.

## 시연 방법

`tools/demo.sh`는 adb와 Virtual Console로 차량 신호를 흉내 냅니다(`ANDROID_HOME` 또는 PATH의 adb 사용).

1. `tools/demo.sh setup` (위치 서비스가 멈췄다면 `--fix-location`)
2. 앱에서 루틴 → 차량 점검(`tools/demo.sh belt on` / `belt off`로 벨트 경고를 켰다 끄면 체크 항목이 바뀝니다) → 길 미리보기 → 출발 화면의 "회사로 안내 시작"
3. `tools/demo.sh drive commute` (약 95초 실시간 재생, 접근 구간 음성 안내)
4. 도착해 P단이 되면 앱이 기록 화면을 엽니다
5. `tools/demo.sh reset`

콘솔 페이지를 열어 둔 상태에서 무드조명 변화가 보입니다.

## 라이선스

- 시·군·구 경계(`assets/regions/sigungu.geojson`): 통계청 SGIS 행정구역 경계(공공누리 1유형)를 가공한 vuski/admdongkor(https://github.com/vuski/admdongkor, CC BY 4.0) 데이터를 단순화·병합하여 사용했습니다. 생성: `tools/build_regions.sh`.
- 루틴 배경음(`assets/music/calm_start.m4a`): Upmile 팀이 `tools/make_music.py`로 직접 생성한 음원(CC0 1.0).
- 글꼴 Asta Sans(`app/src/main/res/font/asta_sans.ttf`): 42dot의 Asta Sans 가변 글꼴(SIL Open Font License 1.1). 라이선스 전문: `assets/licenses/AstaSans-OFL.txt`.

## 개발 메모

- 데모 경로 3개(`assets/routes/{commute,cafe,inlaws}.json`)는 TMAP 키가 생기기 전까지 TMAP 형식으로 직접 만든 합성 데이터입니다. `tools/fetch_route.sh`로 실제 응답으로 교체합니다.
- 차량 신호 어댑터(`PleosVehicleSignals`): 에뮬레이터(Pleos_Connect_v2.1.0)에서 착좌 값이 콘솔과 **반대로** 읽힙니다(콘솔 true → SDK false). 그래서 `DEFAULT_INVERT_SEAT = true`입니다. 조향각 부호도 반대이며 `STEERING_SIGN = -1f`로 보정합니다. 남은 주행 거리는 SDK가 미터로 돌려주므로 km로 환산합니다.
- 차량 무드조명·공조(`PleosCabinController`): 에뮬레이터에서 SDK 쓰기(앰비언트 스위치·밝기·색, 팬 속도)는 서비스 로그상 모두 성공하고 읽기값도 따라갑니다. Virtual Console의 `GET /vehicle/state`(`body.ambient_light`, `hvac.fan_speed`)는 콘솔 브라우저 세션 하트비트(`POST /browser/session`, 약 1초 간격)가 살아 있을 때만 SDK 쓰기를 반영합니다. 하트비트가 없으면 콘솔이 값을 미러링하지 않으니 `adb logcat`의 `[Pleos] setProperty()` 로그로 확인하세요. 앱이 호흡 도중 강제 종료되면 복원이 안 되어 조명이 켜진 채 남을 수 있습니다.
