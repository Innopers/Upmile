# Upmile

운전 중에 가르치지 않습니다. 출발 전 몇 분, 오늘 길의 가장 어려운 곳을 차 안에서 먼저 달려 봅니다.

## 실행 방법

## 테스트 환경

## APK 위치

## 라이선스

- 시·군·구 경계(`assets/regions/sigungu.geojson`): 통계청 SGIS 행정구역 경계(공공누리 1유형)를 가공한 vuski/admdongkor(https://github.com/vuski/admdongkor, CC BY 4.0) 데이터를 단순화·병합하여 사용했습니다. 생성: `tools/build_regions.sh`.
- 루틴 배경음(`assets/music/calm_start.m4a`): Upmile 팀이 `tools/make_music.py`로 직접 생성한 음원(CC0 1.0).
- 글꼴 Asta Sans(`app/src/main/res/font/asta_sans.ttf`): 42dot의 Asta Sans 가변 글꼴(SIL Open Font License 1.1). 라이선스 전문: `assets/licenses/AstaSans-OFL.txt`.

## 개발 메모

- 데모 경로 3개(`assets/routes/{commute,cafe,inlaws}.json`)는 TMAP 키가 생기기 전까지 TMAP 형식으로 직접 만든 합성 데이터입니다. `tools/fetch_route.sh`로 실제 응답으로 교체합니다.
- 차량 신호 어댑터(`PleosVehicleSignals`): 에뮬레이터(Pleos_Connect_v2.1.0)에서 착좌 값이 콘솔과 **반대로** 읽힙니다(콘솔 true → SDK false). 그래서 `DEFAULT_INVERT_SEAT = true`입니다. 조향각 부호도 반대이며 `STEERING_SIGN = -1f`로 보정합니다. 남은 주행 거리는 SDK가 미터로 돌려주므로 km로 환산합니다.
- 차량 무드조명·공조(`PleosCabinController`): 에뮬레이터에서 SDK 쓰기(앰비언트 스위치·밝기·색, 팬 속도)는 서비스 로그상 모두 성공하고 읽기값도 따라가지만, Virtual Console의 `GET /vehicle/state`(`body.ambient_light`, `hvac.fan_speed`)에는 **반영되지 않습니다**(콘솔은 자체 REST로 낸 값만 보여 줌). 확인은 `adb logcat`의 `[Pleos] setProperty()`/`getProperty_onResult` 로그로 합니다. 앱이 호흡 도중 강제 종료되면 복원이 안 되어 조명이 켜진 채 남을 수 있습니다.
