# 주차까지

내비에 목적지를 찍은 뒤 다시 휴대폰을 열지 않고, 현재 차량과 방문 조건에 맞는 주차장을 고르는 Pleos 차량용 앱 공모전 일반 부문 프로젝트.

## 현재 Android 화면

Pleos 기본 내비에서 목적지를 설정하고 앱을 열면 NaviHelper로 방문지를 읽고 Android Automotive의 차량 정보로 입차 조건을 계산한다. 코엑스 북문 GATE 4와 신천유수지의 차량 안내점을 지원한다. 예상 체류 시간과 구매 할인은 **주차비 계산**에서 바꿀 수 있다.

- **공식 공개 정보** · 코엑스 지하 높이, 승용차 기본요금, 10만 원 구매 할인. 2026-11-08 확인. [진입 높이](https://business.coex.co.kr/guide/conference-room-parking-information/general-vehicle/) · [요금·할인](https://www.coex.co.kr/guide/parking-information/fee/)
- **차량 정보** · AAOS `CarPropertyManager`에서 제조사·차종·연식·연료·외부 치수를 읽는다. 높이 조회가 안 되면 현재 등록한 정확한 모델·연식의 공식 기본 제원만 쓴다. 현재 제원표는 2025 Hyundai IONIQ 5 N 한 항목이다. 2026-11-08 확인. [AAOS 속성](https://developer.android.com/reference/android/car/VehiclePropertyIds) · [제조사 제원](https://dmassets.hyundai.com/is/content/hyundaiautoever/IONIQ_5N_MY25pdf)
- **내비와 주차장 데이터** · 코엑스 북문 GATE 4의 차량 안내점을 Pleos 기본 내비와 NaviHelper로 확인했다. 공식 Pleos 에뮬레이터에서만 경로 시험용으로 코엑스 전기차 입차 가능을 가정하고 상세에 표시한다. 이 환경에서 높이 2,350mm를 입력하면 시연용 대안 A를 확인할 수 있다. 대안 A에는 실제 입구 안내가 없다. 실차에서는 코엑스 전기차 입차 조건을 미확인으로 표시한다. [코엑스 진입로](https://www.coex.co.kr/guide/directions/) · [NaviHelper 목적지 안내](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)
- **글꼴** · [42dot Asta Sans 원본](https://github.com/42dot/Asta-Sans/tree/69ecccd58dcb8b506f665867733b17773bc4bb9f)을 앱에 번들한다. OFL은 APK의 `res/raw/asta_sans_ofl.txt` 리소스에 포함한다.

### 공영주차장 빈자리 시연

서울 열린데이터광장 `GetParkingInfo` 공개 샘플을 사용한다. 코엑스에서는 학여울역 공영주차장을, 잠실역에서는 목적지의 공영주차장과 신천유수지 공영주차장을 비교한다. 소스에서 제공하는 일반 요금과 갱신 시각을 표시한다. 데이터가 5분 이내이고 목적지 빈자리가 5% 이하일 때, 빈자리 비율이 5%를 초과하는 24시간 운영 후보를 추천한다. 신천유수지의 직선 거리는 두 주차장 위치 기준이다. [시영주차장 실시간 API](https://data.seoul.go.kr/dataList/OA-21709/A/1/datasetView.do) · [공영주차장 위치 API](https://data.seoul.go.kr/dataList/openApiView.do?infId=OA-13122&srvType=A)

신천유수지는 진입 높이 제한 3.0m와 전기차 입차 가능을 적용한다. 공단은 전기차 충전 이력 증빙 시 첫 1시간 무료와 이후 50% 감면을 안내한다. 차량 안내는 오금로 1의 순정 Pleos 주차장 POI를 사용한다. 공영주차장 시설 핀과 별도로 순정 내비의 차량 안내점(37.52190833, 127.10321944)을 2026-11-10 확인했다. 새 안내는 해당 지점과 POI가 일치할 때 확인한다. [시설·요금 안내](https://www.sisul.or.kr/open_content/parking/bbs/bbsMsgDetail.do?bcd=parking&msg_seq=43) · [승용차 5부제 해제](https://www.sisul.or.kr/open_content/parking/bbs/bbsMsgDetail.do?bcd=notice&msg_seq=8791)

공개 샘플은 구별 첫 5행만 조회한다. 코엑스 지하의 빈자리는 현재 연결한 자료에서 제공되지 않으며, 학여울역은 요금과 빈자리만 비교한다. 신천유수지 안내는 차량 조건을 충족하고 최근 빈자리가 확인될 때 사용자가 명시적으로 요청할 수 있다. 서울시 공식 샘플 호스트에만 HTTP를 허용하고 앱이 활성화된 동안 분마다 갱신한다. 갱신에 실패하면 주차장과 마지막 확인 요금을 유지한다. 원본 갱신 시각에서 5분이 지나면 빈자리, 혼잡 추천, 신천유수지 입구 요청이 만료된다.

## 제출용 APK

[최재훈_주차까지_release.apk](apk/%EC%B5%9C%EC%9E%AC%ED%9B%88_%EC%A3%BC%EC%B0%A8%EA%B9%8C%EC%A7%80_release.apk)는 서명된 Pleos용 릴리스 0.1.1이다.

1. Pleos Connect Emulator 2.1.0 (Android API 34)에서 `adb install "apk/최재훈_주차까지_release.apk"`로 설치한다. 예전 디버그 APK가 다른 키로 서명되어 있으면 테스트 기기에서 기존 주차까지를 제거한 뒤 설치한다.
2. 순정 Pleos 내비에서 **코엑스**로 안내를 시작하고 주차까지를 연다. 추천 카드에서 차량 높이와 입구 제한을 확인하고 **총높이 보완**으로 2,350mm를 적용하면 코엑스 높이 초과와 대안 A가 표시된다. 같은 버튼에서 **보완값 삭제**로 기본 차량 높이를 복원한다.
3. 순정 내비에서 **잠실역 2호선**으로 안내를 시작하고 주차까지를 열면 잠실역과 신천유수지의 실시간 요금·빈자리를 확인할 수 있다. 추천 여부는 조회 시점의 혼잡도에 따라 바뀐다. 신천유수지에 **입구 안내**가 표시되면 선택 후 **내비 안내**를 눌러 순정 내비 목적지를 주차장 입구로 바꾼다.

APK SHA-256 `af2cbc46181e8c19672dc112c4153956e2fbc68d2dfaebcb0b0d313c07f34549`

### 제출 파일 검증 (2026-11-10)

| 시나리오 | 동일한 서명 APK를 Pleos 에뮬레이터에 설치해 확인한 결과 |
| --- | --- |
| 코엑스 차량 조건 | 순정 내비 목적지와 차량 속성 높이 1,776mm를 수신했다. 추천 카드에서 내 차 1.78m와 코엑스 제한 2.20m를 비교했다. 카드에서 총높이 2,350mm를 보완하자 시연용 대안 A로 바뀌었고 보완값 삭제 후 코엑스 추천으로 돌아왔다. |
| 잠실역 실시간 조회 | 순정 내비 목적지를 수신했다. 18:43 KST 갱신 값에서 잠실역 90/357면, 신천유수지 81/192면과 각각 5분 440원을 표시했다. |
| 잠실역 혼잡 재현 | 같은 날 15:21 KST의 공개 API 응답(10/357면, 17/192면)을 테스트 환경에서 갱신 시각만 조정해 재생하자 신천유수지를 추천했다. |
| 잠실역 갱신 실패 및 만료 | 다음 공개 API 요청을 테스트 환경에서 HTTP 503으로 응답했다. 두 주차장과 마지막 요금은 유지됐다. 데이터 시각에서 5분 후 빈자리는 확인 불가로 바뀌고 혼잡 추천과 신천유수지 입구 안내가 사라졌다. |
| 잠실역에서 신천유수지 입구 안내 | 정상 네트워크로 복원해 실시간 빈자리를 다시 불러왔다. 오금로 1 입구를 선택하자 순정 내비가 신천유수지 안내를 시작했고, 앱은 잠실역을 방문지로 유지하면서 내비 요청 수락을 표시했다. |

릴리스 서명, 패키지 ID와 비디버그 설정, 설치된 APK의 SHA-256 일치를 검증했다. 디버그 및 릴리스 JVM 테스트는 각각 153개 통과했고, 릴리스 빌드의 필수 Android Lint를 통과했다.

## 빌드와 검증

JDK 17, Gradle 8.9, Android SDK Platform 35와 Build Tools 34.0.0을 사용한다.

```sh
gradle --no-daemon --console=plain :app:testPleosDebugUnitTest :app:assemblePleosDebug :app:testPleosReleaseUnitTest :app:assemblePleosRelease
```

기본 출력은 `app/build/outputs/apk/pleos/debug/app-pleos-debug-unsigned.apk`다. 별도 설치 테스트를 승인받으면 `-PsignLocalDebug`로 로컬 디버그 서명 빌드를 만들 수 있다.

릴리스 빌드 출력은 `app/build/outputs/apk/pleos/release/app-pleos-release-unsigned.apk`다. 제출 파일은 별도 릴리스 키로 서명하고 `apksigner verify --verbose --print-certs`로 검증한다.

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
