# 주차까지

내비에 목적지를 찍은 뒤 다시 휴대폰을 열지 않고, 현재 차량과 방문 조건에 맞는 주차장을 고르는 Pleos 차량용 앱 공모전 일반 부문 프로젝트.

## 현재 Android 화면

앱을 열면 Android Automotive의 차량 정보로 입차 조건을 계산한다. 입구 안내가 가능한 부설 주차장을 먼저 표시하고 대안은 아래에 표시한다. 체류 시간과 구매 할인 가정은 상세 화면의 요금 조건에서 바꿀 수 있다.

- **공식 공개 정보** · 코엑스 지하 높이, 승용차 기본요금, 10만 원 구매 할인. 2026-11-08 확인. [진입 높이](https://business.coex.co.kr/guide/conference-room-parking-information/general-vehicle/) · [요금·할인](https://www.coex.co.kr/guide/parking-information/fee/)
- **차량 정보** · AAOS `CarPropertyManager`에서 제조사·차종·연식·연료·외부 치수를 읽는다. 높이 조회가 안 되면 현재 등록한 정확한 모델·연식의 공식 기본 제원만 쓴다. 현재 제원표는 2025 Hyundai IONIQ 5 N 한 항목이다. 2026-11-08 확인. [AAOS 속성](https://developer.android.com/reference/android/car/VehiclePropertyIds) · [제조사 제원](https://dmassets.hyundai.com/is/content/hyundaiautoever/IONIQ_5N_MY25pdf)
- **시연 입력** · 목적지, 입구 좌표와 주변 대안은 시연값이다. 경로 요청은 `SampleTrip.SimulatedNavigation`에서 처리하며 화면에 시연 요청으로 표시한다. 차량 속성의 실제 Pleos 런타임 가용성은 별도로 확인한다.
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

입구 되울림은 어댑터가 요청의 `requestId`를 `echoedRequestId`로 돌려주는 계약이다. 이미 보낸 요청의 되울림은 방문지를 바꾸지 않는다. 상관 ID 없는 이벤트는 같은 위치라도 새 방문지다. 이는 앱 내부 설계이며 NaviHelper가 해당 ID나 콜백을 제공한다는 주장이 아니다. 실제 어댑터는 SDK의 공개 의존성·호출 조건과 되울림 식별 가능성을 확인한 뒤 별도 구현해야 한다. 현재 어댑터는 SDK를 호출하지 않는다.

| 통과 항목 | 결정적 fake 테스트 (`ParkingNavigationTest`) |
| --- | --- |
| 최신 목적지만 활성화·이전 선택 요청 차단 | `newestVisitOwnsOptionsAndInvalidatesOldSelection` |
| 확인 전 0건·확인 후 선택 입구로 1건 | `confirmationAloneSendsExactlyOnceWithChosenCoordinates` |
| 실패·시연·수락 구분 | `rejectionSimulationAndExceptionAreNotSuccess` |
| 동기·지연 되울림 시 원래 방문지 유지·새 방문지 교체 | `synchronousAndDelayedEntranceEchoPreserveVisitButNewVisitReplacesIt` |
| 조건 변경·높이 및 미확인 제한 유지 | `changedVehicleConditionsInvalidateOpenConfirmation`, `restrictedAndUnknownOptionsCannotBeSelected` 및 기존 `ParkingPlanTest` |

후보 `1b3e2716204f023c2adabdec7d4839be3503b074`는 사용자 제공 검토 보고상 Actions `42481652866`에서 JVM 테스트 13건(실패·오류·건너뜀 각 0건)과 unsigned APK 빌드가 성공했다. 보고 수령일은 2026-11-08이며 이번 작업에서 직접 재검증하지 않았다. APK 해시와 근거 수준, 제출 단계는 [후보별 검증 상태](contest-brief.md#후보별-검증-상태)에 기록한다.

과거 로컬 시도 (2026-11-08): `gradle --offline --no-daemon --console=plain :app:testPleosDebugUnitTest :app:assemblePleosDebug`는 `gradle: command not found`로 실행되지 않았고, 당시 `java`와 `javac`도 PATH에 없었다. 이는 당시 환경의 기록이다. 문서 정정을 포함한 이후 변경 HEAD는 별도 검증 대상이다. 실제 SDK 콜백, Windows 에뮬레이터 설치·화면, 차량·음성·음향 시험 및 릴리스·공개 배포 증거는 미확보다.
