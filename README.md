# 주차까지

내비에 목적지를 찍은 뒤 다시 휴대폰을 열지 않고, 현재 차량과 방문 조건에 맞는 주차장을 고르는 Pleos 차량용 앱 공모전 일반 부문 프로젝트.

## 현재 Android 화면

Pleos 기본 내비에서 목적지를 설정하고 앱을 열면 NaviHelper로 방문지를 읽고 Android Automotive의 차량 정보로 입차 조건을 계산한다. 현재 실제 목적지에 연결된 시설 데이터는 코엑스에 한정한다. 예상 체류 시간과 구매 할인은 **주차비 계산**에서 바꿀 수 있다.

- **공식 공개 정보** · 코엑스 지하 높이, 승용차 기본요금, 10만 원 구매 할인. 2026-11-08 확인. [진입 높이](https://business.coex.co.kr/guide/conference-room-parking-information/general-vehicle/) · [요금·할인](https://www.coex.co.kr/guide/parking-information/fee/)
- **차량 정보** · AAOS `CarPropertyManager`에서 제조사·차종·연식·연료·외부 치수를 읽는다. 높이 조회가 안 되면 현재 등록한 정확한 모델·연식의 공식 기본 제원만 쓴다. 현재 제원표는 2025 Hyundai IONIQ 5 N 한 항목이다. 2026-11-08 확인. [AAOS 속성](https://developer.android.com/reference/android/car/VehiclePropertyIds) · [제조사 제원](https://dmassets.hyundai.com/is/content/hyundaiautoever/IONIQ_5N_MY25pdf)
- **내비와 주차장 데이터** · 코엑스 북문 GATE 4의 차량 안내점을 Pleos 기본 내비와 NaviHelper로 확인했다. 공식 Pleos AVD의 디버그 빌드에서는 경로 시험을 위해 전기차 입차 가능을 가정하고 상세에 표시한다. 일반 빌드는 전기차 입차 조건을 미확인으로 처리한다. 이전 시연 데이터는 판단 로직 테스트에 사용한다. [코엑스 진입로](https://www.coex.co.kr/guide/directions/) · [NaviHelper 목적지 안내](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)
- **글꼴** · [42dot Asta Sans 원본](https://github.com/42dot/Asta-Sans/tree/69ecccd58dcb8b506f665867733b17773bc4bb9f)을 앱에 번들한다. OFL은 APK의 `res/raw/asta_sans_ofl.txt` 리소스에 포함한다.

## 빌드와 검증

JDK 17, Gradle 8.9, Android SDK Platform 35와 Build Tools 34.0.0을 사용한다.

```sh
gradle --no-daemon --console=plain :app:testPleosDebugUnitTest :app:assemblePleosDebug
```

기본 출력은 `app/build/outputs/apk/pleos/debug/app-pleos-debug-unsigned.apk`다. 별도 설치 테스트를 승인받으면 `-PsignLocalDebug`로 로컬 디버그 서명 빌드를 만들 수 있다.

GitHub Actions의 `pleos-emulator-validation.yml`은 Ubuntu 24.04에서 PR의 실제 HEAD를 체크아웃해 JVM 테스트와 APK 빌드를 실행한다. 성공한 실행은 `pleos-build-<full SHA>`에 아래 파일을 보관한다.

| 파일 | 의미 |
| --- | --- |
| `source-sha.txt` | 체크아웃한 커밋의 전체 SHA |
| `input-sha256.txt` | Git 추적 파일 전체의 경로별 SHA-256 |
| `apk-sha256.txt` | 배포 전 unsigned APK의 SHA-256 |
| `app-pleos-debug-unsigned.apk` | Pleos 대상 빌드 변형의 unsigned APK |
| `gradle.log`, `test-results/` | Gradle 로그와 JVM 테스트 결과 XML |

아티팩트 폴더에서 `sha256sum -c apk-sha256.txt`로 APK 무결성을 확인한다. 동일 SHA를 체크아웃한 저장소에서 `sha256sum -c /path/to/input-sha256.txt`로 입력을 검증할 수 있다. 이 워크플로의 증거 범위는 빌드·JVM 테스트다.

[공모전 기획](contest-brief.md) · [시연 리허설](showcase/REHEARSAL.md) · [작업 루프](AGENTS.md)

## 목적지와 입구 요청 계약

`NavigationPort`는 목적지 이벤트와 입구 요청의 주입 경계이며 `ParkingNavigation`은 최신 방문지, 선택 만료, 확인 및 결과 상태를 관리한다. 방문지별 주차 목록은 이벤트에 포함한다. 새 목적지를 받으면 이전 선택은 만료된다. 확인 전에는 요청하지 않고 각 선택은 확인 시 한 번만 소비한다. 차량·방문 조건을 다시 표시할 때도 열린 확인의 선택을 만료시킨다. 거절 또는 예외는 실패로, 시연 처리는 실제 요청 수락과 별도로 표시한다.

실제 어댑터는 NaviHelper 3.0.5를 초기화해 목적지 정보를 조회한다. 사용자가 입구를 확인하면 `requestRoute()`를 호출하고 결과 대기 상태를 표시한다. 경로 완료 또는 안내 상태를 받고 조회된 목적지가 요청한 입구와 일치하면 완료로 기록한다. 이 경우 원래 방문지를 주차 앱에 유지한다. SDK 오류나 응답 시간 초과는 실패로 기록한다. 요청 ID는 앱 내부 값이다.

| 통과 항목 | 결정적 fake 테스트 (`ParkingNavigationTest`) |
| --- | --- |
| 최신 목적지만 활성화·이전 선택 요청 차단 | `newestVisitOwnsOptionsAndInvalidatesOldSelection` |
| 확인 전 0건·확인 후 선택 입구로 1건 | `confirmationAloneSendsExactlyOnceWithChosenCoordinates` |
| 실패·시연·수락 구분 | `rejectionSimulationAndExceptionAreNotSuccess` |
| 비동기 결과를 해당 요청과 방문지에 적용 | `pendingRouteOnlyCompletesForTheMatchingRequestAndVisit` |
| 동기·지연 되울림 시 원래 방문지 유지·새 방문지 교체 | `synchronousAndDelayedEntranceEchoPreserveVisitButNewVisitReplacesIt` |
| 조건 변경·높이 및 미확인 제한 유지 | `changedVehicleConditionsInvalidateOpenConfirmation`, `restrictedAndUnknownOptionsCannotBeSelected` 및 기존 `ParkingPlanTest` |

이전 후보의 빌드·검수 이력은 [후보별 검증 상태](contest-brief.md#후보별-검증-상태)를 따른다.
