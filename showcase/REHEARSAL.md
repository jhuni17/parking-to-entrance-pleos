# 주차까지 · 시연 리허설

## 현재 Android 후보

첫 화면에 목적지, 주차장 이름, 두 시간 예상 요금과 입구 안내를 확인한다. 화면에는 Asta Sans, 밝은 회색 바탕과 짙은 주요 버튼을 사용한다. 차량 높이와 연료 유형은 Android Automotive 서비스에서 읽으며, 일반 화면의 수동 차량 설정은 없다. 높이를 확인하지 못한 경우에는 안내 가능한 주차장을 주장하지 않는다. 요금 가정과 차량 제원 출처는 상세에서 확인한다.

실제 런타임 리허설에서는 AAOS 속성으로 차종과 연료 유형이 수신되는지, 외부 치수를 지원하는지, 높이 제한과 전기차 정책에 따라 예상한 후보가 뜨는지를 확인한다. 목적지와 주차장 대안은 시연값이다. 아래 이전 단계 기록은 당시의 합성 시나리오다.

## 현재 앱 리허설 절차 (소스 대조일 2026-11-08)

이 절은 향후 화면 리허설 대본이며 실행 관측 기록이 아니다. 차량 수치를 화면에서 입력하는 단계는 없다.

1. 앱을 연다. `onCreate`는 시연 목적지 **스타필드 코엑스몰**을 설정한다. 최초 요금 조건은 120분·구매 할인 미적용이다. `onStart` 조회 동안 “차량 정보 확인 중”을 기다린다.
2. 조회 결과를 확인한다. 종료 후 **“차량 높이 확인 필요”**라면 입구 요청 불가다. 후보 행을 눌러 상세·요금 조건까지만 보고 요청 단계로 진행하지 않는다. 높이를 받았어도 요청 가능 후보가 없으면 “입구 안내 가능한 주차장 없음”으로 요청을 중단한다. 빈 시설 목록은 “시설 정보 없음”으로 구분한다. 후보 수 숫자는 화면에 표시되지 않는다(`refresh`).
3. 추천 카드의 **“상세”** 또는 다른 후보 행을 누른다. 상세에서 입구 높이 제한·차량 높이·연료 유형·높이 출처·판정 사유·자료 출처를 읽는다. **“요금 조건”**에서 **“2시간 기준 (해제 시 1시간)”**, **“구매 할인 조건 충족 가정”**을 확인하고 **“적용”**으로 반영하거나 **“취소”**한다. 할인은 실제 영수증 검증이 아닌 가정이다(`showDetails/showQuoteSettings`).
4. 요청 가능한 경우만 추천 카드의 **“입구 안내 ↗”** 또는 상세의 **“입구 안내”**를 누른다. **“이 입구로 안내할까요?”** 확인창에 입구 이름과 “시연 경로 요청”이 나온다. **“취소”**하면 요청하지 않는다. 다시 선택해 **“시연 요청”**을 누르면 선택한 입구로 한 번 요청하고 결과 **“시연 요청 완료”**를 기대한다. 조건이 바뀐 선택은 “조건이 바뀌었습니다. 입구를 다시 선택하세요.”로 처리한다(`confirmPreview`).
5. 확인창이 닫힌 뒤 목록 밖 **입구 이름 · 결과 요약**을 누른다. **“마지막 요청”** 상세의 방문지·입구·좌표·결과를 확인하고 **“닫기”**를 누른다(`refresh/showReceipt/routeStatus`). 기록이 없으면 이 요약은 숨겨진다.

해설: 실제 길안내 시작이 아니라 시연 어댑터 요청 처리다. 차량 조건별 예상 요금·순서는 아래 공통 표를 참고하며 해당 차량 신호가 실제 수신되었다고 가정하지 않는다. 후보 CI 보고와 Windows 화면 관측은 별개다. 제출 단계는 [제안서의 제출 단계와 요구 자료](../contest-brief.md#제출-단계와-요구-자료)를 따른다. 1차는 제안서·서명 동의서 필수, 적격 GitHub/APK·프로토타입 선택이며 최종 PT는 작동 앱 시연 필수다.

## 실제 NaviHelper 연동 이후

기본 내비에서 목적지를 선택한 화면, 앱의 목적지 수신, 사용자가 선택한 입구, 기본 내비의 경로 요청 결과를 차례로 기록한다. 문서에 있는 API, 시연용 입력, 런타임 관측을 구분한다. 실제 안내에 사용하는 입구는 공개된 시설 자료에서 확인한 좌표를 사용한다.

## 후보 계약 검증 상태 (보고 수령일 2026-11-08)

`ParkingNavigationTest`는 가짜 내비의 연속 목적지 이벤트, 확인 전 요청 0건, 확인의 중복 소비 차단, 거절·예외, 요청 ID가 붙은 동기·지연 입구 되울림과 새 방문지를 검사한다. 시연 어댑터는 실제 내비 수락을 반환하지 않는다. 실패 UI는 “입구 경로 요청 실패”로 분리되어 있으며 현재 화면의 시연 어댑터는 실패를 발생시키지 않는다.

사용자 제공 검토 보고상 위 후보는 Actions `42481652866`에서 `testPleosDebugUnitTest`·`assemblePleosDebug`가 성공했고, 테스트 13건의 실패·오류·건너뜀은 각 0건이다. 이번 작업에서는 직접 재검증하지 않았다. unsigned APK 해시와 제출 단계별 근거 범위는 [검증 상태 표](../contest-brief.md#후보별-검증-상태)를 따른다.

과거 로컬 Gradle 시도는 도구 부재로 실행되지 않았다 (`gradle: command not found`; 당시 Java 컴파일 도구도 PATH에 없음). 이는 역사적 환경 기록이며 후보의 보고된 CI 성공과 구분한다. 문서 정정을 포함한 이후 변경 HEAD는 별도 검증 대상이다. Windows 설치·화면 관측, 실제 NaviHelper 콜백과 실제 신호에 따른 사용자 결과, 차량·음성·음향 시험 및 릴리스 증거는 미확보다. Windows·실차 검증은 별도 승인 범위이며, 요청 ID를 SDK 이벤트에 연결할 수 있는지도 실제 연동 전 확인해야 한다.

## 현재 판단·조건별 기대 결과 (소스 대조일 2026-11-08)

최초 시작은 **120분·구매 할인 미적용**이다. 차량은 `onStart`에서 UNKNOWN으로 초기화한 뒤 AAOS에서 다시 조회한다. 고정 차량 높이나 비전기차 기본값을 넣지 않는다. `ParkingPlan.compare`의 정렬은 요청 가능 여부 → Fit → 부설 우선 → 예상 요금 순이다. 입구 누락은 명시적 입차 금지가 아니라 “입구 정보 미확인”이다. `candidateSummary`의 후보 수·빈 목록 문구는 JVM 테스트 대상이며 현재 `MainActivity.refresh`는 이를 화면에 표시하지 않는다. 화면 상태는 조회 중 “차량 정보 확인 중”, 조회 종료 후 높이 누락이면 “차량 높이 확인 필요”, 높이는 있지만 요청 가능 0건이면 “입구 안내 가능한 주차장 없음”이다.

아래 표는 두 문서에 동일하게 적용한다. 합성 행의 수치는 기존 입력·요금식·assertion을 대조한 계산 결과이며 화면의 후보 수 표시나 실제 차량 신호 관측을 뜻하지 않는다. 차량 조건을 바꾸는 수동 화면 조작은 없다.

| 구분·조건 | 기대 결과 | 기존 소스·테스트 근거 |
| --- | --- | --- |
| 현재 화면 조작: 최초 시작 → 조회 종료, 높이 미확인 | “차량 높이 확인 필요”; 입구 요청 불가. 상세·요금 조건까지만 확인 | `MainActivity.onCreate/onStart/refresh/showDetails`, `ParkingPlan.evaluate` |
| JVM 합성: 높이 미확인·NON_ELECTRIC·120분·할인 미적용 | 요청 가능 0건; 높이 확인 필요로 요청 차단 | `VehicleProfileTest.missingOrInvalidDimensionsCannotCreateARequestableOption`, `VehicleReadResultTest.twoDisconnectedReadsTerminateUnknownWithoutDemoOrFuelAssumptions` |
| JVM 합성: 1850mm·NON_ELECTRIC·120분·할인 미적용 | 코엑스 12,000원이 대안 A 6,000원보다 먼저; 요청 가능 2건. 부설 우선 정렬 | `ParkingPlanTest.noPurchaseConditionShowsFullPriceAndKeepsUsableOnSiteOptionFirst`, `ParkingPlan.compare`, `SampleTrip.LOTS` 요금식 |
| JVM 합성: 1850mm·NON_ELECTRIC·120분·할인 적용 | 코엑스 예상 0원 우선, 기본 요금 12,000원; 요청 가능 2건 | `ParkingPlanTest.purchaseConditionMakesAttachedLotFirst`, `SampleTrip.LOTS` |
| JVM 합성: 2250mm·NON_ELECTRIC·120분·할인 미적용 | 대안 A 6,000원 우선, 요청 가능 1건; 코엑스 높이 제한 초과로 요청 불가 | `VehicleProfileTest.hardwareHeightAndWidthOverrideCatalogAndTallVehicleUsesAlternative`, `VehicleReadResultTest.disconnectedThenSuccessAutomaticallyRecalculatesWithHardware`, `ParkingPlan.evaluate`, `SampleTrip.LOTS` |
| JVM 합성: 1850mm·ELECTRIC·120분·할인 미적용 | 대안 A 6,000원 우선, 요청 가능 1건; 코엑스 전기차 정책 미확인으로 요청 불가(명시적 금지와 구별) | `VehicleProfileTest.electricAndAmbiguousFuelDoNotBypassUnknownEvPolicy`, `ParkingPlanTest.unknownEvPolicyAndEvBanAreDifferent`, `ParkingPlan.evaluate`, `SampleTrip.LOTS` |
| JVM 합성: 입구 없는 무료 시설·입구 있는 유료 시설 / 모두 요청 불가 / 빈 목록 | 각각 입구 있는 시설 우선·1건 / 0건 / 0건; 후보 요약은 테스트 결과용 문구 | `ParkingPlanTest.entranceReadyLotPrecedesFreeLotWithoutEntrance`, `allUnavailableLotsHaveNoRequestableEntrance`, `emptyLotsHaveNoFacilityInformation` |
| 현재 화면 조작: 가능한 입구 선택 → 취소 또는 “시연 요청” | 확인 전 요청 0건; 취소는 전송 없음. 확인 후 선택 좌표로 1회, “시연 요청 완료” | `MainActivity.confirmPreview/routeStatus`, `ParkingNavigationTest.confirmationAloneSendsExactlyOnceWithChosenCoordinates`, `SampleTrip.SimulatedNavigation` |
| 향후 실제 AAOS 관측: 조회·재조회·재생성·요청 결과 | 실제 반환 높이·연료와 위 판단의 일치, 오래된 조회 배제, 기록 초기화 등을 관측해야 함. 현재 미관측 | `MainActivity.onStart/onStop/onSaveInstanceState`, `VehicleReadResultTest`의 세션 합성 검사; 실제 Handler·Activity·SDK 관측의 대체 증거 아님 |

시설 공개값의 출처·확인일은 `SampleTrip.java`의 코엑스 공식 높이·요금·할인 안내(2026-11-08 확인)를 따른다. 이번에는 외부 재확인하지 않았다. 입구 좌표와 대안 A는 시연값이며 대안 B는 높이·입구 정보가 누락되어 요청할 수 없다. 합성 assertion 대조는 이번 HEAD의 테스트 실행 성공을 뜻하지 않는다.

## 현재 요청 기록과 재생성

`MainActivity.refresh`는 요청 전에는 기록을 숨긴다. 확인 후에는 목록 밖에 **입구 이름 · 시연 요청 완료** 요약을 표시한다. 이 요약을 누르면 `showReceipt`의 **“마지막 요청”** 상세에서 방문지·입구·좌표·결과를 확인한다. 예를 들어 대안 A 요청은 스타필드 코엑스몰 / 대안 A 입구 (시연 좌표) / 37.51400, 127.06100 / 시연 요청 완료다. 코엑스 입구의 시연 좌표는 37.51200, 127.06000이다. 해설: 시연 어댑터 처리이며 실제 길안내 시작이 아니다. 이 해설은 화면 결과 문구가 아니다.

일반 조건 갱신은 과거 기록을 유지한다. 새 방문지는 기록을 지우고 요청 ID가 연결된 입구 되울림은 원래 방문지와 기록을 유지한다. 근거는 `ParkingNavigationTest.simulatedAlternativeReceiptStartsEmptyAndSurvivesConditionChanges`, `echoesKeepReceiptAndNewVisitClearsIt`, `visitDuringRequestCannotReceiveOldOutcome`이다. 실패 결과는 `routeStatus`에서 “입구 경로 요청 실패”, 실제 수락 상태는 “내비 요청 수락”으로 구분한다. 현재 시연 어댑터는 SIMULATED만 반환하므로 실패·실제 수락의 화면 관측을 주장하지 않는다. 거절·예외의 계약 근거는 `rejectionReplacesPreviousReceiptAndDuplicateDoesNothing`, `exceptionReplacesPreviousReceiptAndDuplicateDoesNothing`이다.

Activity 재생성에서는 **체류 시간과 구매 할인 두 값만 복원**한다. `onSaveInstanceState`가 두 키를 저장하고 `onCreate`가 읽는다. 차량은 `onStart`에서 다시 조회하며, 요청 기록·선택·확인창은 복원하지 않고 자동 재전송도 하지 않는다. 실제 Android 재생성은 미관측이며 소스 경로 대조만 수행했다.

## 이전 구현 기록 — 현재 절차에 적용하지 않음

아래 항목은 과거 profile-restore·route-receipt 등 후보에 적용했던 설명의 이력이다. 현재 후보의 기능이나 현존 테스트 근거로 사용하지 않는다.

- 수동 루프박스·전기차 토글, 1.85 m 고정 시작, 최초 할인 적용은 이전 시연 입력이었다. 현재 일반 화면에는 차량 수동 토글이 없다.
- 삭제된 `DemoProfile`·`DemoProfileTest`의 네 값·네 키 복원 및 16조합 왕복 검사는 과거 profile-restore 기록이다. 현재 복원 근거는 위 MainActivity 두 값 경로뿐이다.
- “마지막 요청 없음”, “마지막 요청 · 과거 확인 기록 (현재 후보 판단과 별개)”, “시연 어댑터 요청 처리 · 실제 길안내 시작 아님”을 화면에 기대했던 기록은 이전 UI 설명이다. 현재 요약·상세·결과 문구는 위 절을 따른다.
- 당시 selection-ready(`b8af43df19b58ff22e41c92f1a610eb3cdfe9dab` 기준), profile-restore(`589948ea1ffe72992434f36c9f264cfa5834d703` 기준), route-receipt(`d7e43092e48d140d5fb363f2d69ae4ef73291c53` 기준)의 로컬 Gradle 시도는 `gradle: command not found`(127)로 미실행이며 Java 도구도 없었다는 기록이다. 당시 지정 다섯 파일 diff 확인은 이번 두 문서 변경의 검증과 별개다. 과거 후보의 CI·XML·APK를 이번 결과로 사용하지 않는다.

## 추천 판단 설명 (decision-basis · 소스 대조일 2026-11-09)

이 절은 현재 구현과 합성 검증 입력을 설명한다. 실제 AAOS/Pleos 속성 수신이나 Windows 화면 관측 기록이 아니다. `RecommendationExplanation.create`는 카드 계산에 사용한 동일 `VehicleProfile`과 이미 정렬된 `Option` 목록을 읽는다. 적합성·요금·정렬·요청 가능 여부를 다시 계산하지 않는다. 설명 생성과 갱신에는 내비 요청 부작용이 없으며 선택 확인만 요청을 발생시킨다.

첫 화면에서는 요청 가능한 대안이 추천될 때만 부설 후보가 제외된 이유 한 줄을 추가한다. 정상 부설 추천에는 추가 설명을 넣지 않는다. 추천 카드 또는 후보 행의 **상세**에서 차량 높이(mm)·연료 유형·높이 출처와 해당 시설의 제한 높이(m·mm), 판정 사유를 확인한다. 높이가 하드웨어에서 왔으면 **차량 속성값**, 정확한 모델·연식의 보완값이면 **제조사 기본 제원**과 제원 URL을 표시한다. 조회 중에는 카드를 비우고 열린 상세도 닫아 이전 높이·추천 이유를 지운다. 목적지 변경도 목록과 설명을 새 결과로 교체하고 열린 상세를 닫는다. 빈 목록은 **시설 정보 없음**으로 구별한다.

아래는 `RecommendationExplanationTest`의 합성 입력 → 기존 판정 → 설명 assertion과 대조한 향후 리허설 기대값이다. 수동 차량 입력 화면이나 실제 차량 관측을 뜻하지 않는다. 모든 요금은 120분·할인 미적용이다.

| 합성 입력 | 첫 화면과 상세 확인 절차 | 테스트 메서드 |
| --- | --- | --- |
| 1850mm·비전기차 | 코엑스 12,000원 우선, 대안 A 6,000원 유지. 추가 이유 없음. 상세에서 1850mm·차량 속성값·비전기차 확인 | `normalHardwareKeepsAttachedPriceAndAddsDetailsOnly` |
| 2250mm·비전기차 | 대안 A 추천 카드의 “코엑스 지하주차장 · 높이 초과” 한 줄 확인. 상세에서 차량 2250mm, 코엑스 행 상세에서 제한 2200mm 확인. 코엑스 RESTRICTED·요청 불가 | `tallHardwareExplainsExistingRestrictionAndLimit` |
| 1850mm·ELECTRIC | 대안 A 추천 이유 “코엑스 지하주차장 · 전기차 입차 조건 미확인”. 명시적 금지로 해설하지 않는다. 코엑스 NEEDS_CHECK·요청 불가 | `electricUnknownPolicyAndUnknownFuelAreNotBansOrConventional` |
| 1850mm·연료 NEEDS_CHECK | 상세는 “연료 유형 확인 필요”; 비전기차로 표시하지 않는다. 코엑스 연료 확인 필요·NEEDS_CHECK·요청 불가, 대안 A 요청 가능 | 같은 메서드 |
| 정확한 HYUNDAI·IONIQ 5 N·2025, 치수 누락·ELECTRIC | 상세에서 1585mm·제조사 기본 제원·URL 확인. 전기차 조건 미확인으로 대안 A 추천 | `exactCatalogFallbackIsReplacedByValidHardware` |
| 같은 식별·하드웨어 2250mm·ELECTRIC | 상세에서 2250mm·차량 속성값 확인, 기본 제원 표시는 사라짐. 코엑스 높이 초과로 대안 A 추천 | 같은 메서드 |
| 정상 결과 → 조회 중 → 높이 UNKNOWN → 빈 목록 | 이전 높이·추천 이유 제거, 조회 종료 후 높이 확인 필요·요청 가능 0건 유지. 빈 목록은 시설 정보 없음. 설명 갱신과 선택까지 요청 0건, 대안 확인 후 37.51400, 127.06100으로 정확히 1건 | `updatesClearOldValuesAndOnlyConfirmationRequestsSelectedEntrance` |
| 입력 차량과 다른 합성 Option 판정·순서 | 전달된 판정과 순서 그대로 설명; 재판정·재정렬 없음 | `explanationUsesSuppliedVerdictAndOrderWithoutRecalculation` |

외부 제원·시설값 출처와 확인일은 기존 README 및 `VehicleProfile.CATALOG_SOURCE`, `SampleTrip`의 2026-11-08 확인 기록을 따른다. 이번에는 네트워크 재확인을 하지 않았다. 대안 A와 입구 좌표는 시연값이다. 1차는 **제안서·서명 동의서 필수**, 적격 GitHub/APK·프로토타입 선택이며, 최종 PT는 **작동 앱 시연 필수**라는 구분을 유지한다.

이번 로컬 환경에는 PATH의 Java·javac·Gradle이 없어 JVM 테스트와 unsigned APK 빌드를 실행하지 못했다. 도구 설치·커밋·푸시·CI 호출은 하지 않았다. `git diff --check`와 승인된 다섯 경로 제한 검사를 수행하며, 이 검사는 테스트 실행 성공을 뜻하지 않는다. 위 신규 테스트와 기존 `ParkingNavigationTest` 등의 XML은 아직 확보하지 못했다.

후속 구현에서 Oracle이 만든 **새 후보 전체 SHA**에 대한 기존 GitHub Actions 결과를 확인해야 한다. `pleos-build-<full SHA>` 이름과 `source-sha.txt`를 대조하고, 동일 SHA 체크아웃에서 README의 `sha256sum -c /path/to/input-sha256.txt`, 아티팩트 폴더에서 `sha256sum -c apk-sha256.txt`를 확인한다. `gradle.log`의 `testPleosDebugUnitTest`·unsigned `assemblePleosDebug` 성공, 기존·신규 테스트 XML의 실패·오류·건너뜀 모두 0, 워크플로 APK 패키지·ZIP·unsigned 검사 성공을 확인할 때까지 실행 수용 항목 8은 대기다. 기준 SHA나 과거 성공 보고로 대체하지 않는다.

Windows 설치·화면·재생성, 실제 SDK 콜백·차량 신호, 실차·음성·음향·공개 배포 증거는 없다. 별도 승인된 환경에서 위 기대값과 실제 화면·신호를 대조해야 한다.


## 차량 서비스 연결 지연 재조회 (vehicle-service-reconnect · 2026-11-08)

이번 소스는 최초 읽기의 서비스 미연결과 연결 후 개별 속성 누락을 구별한다. `Car` 생성 결과가 없거나 연결되지 않았거나 속성 서비스 객체가 없으면 `DISCONNECTED`로 분류한다. 이 첫 결과에만 500ms 뒤 백그라운드 읽기를 한 번 더 예약한다. 두 번째 결과로 반드시 종료한다. 연결된 서비스에서 개별 속성이 미지원·접근 불가·사용 불가이면 해당 값만 누락되며 재시도하지 않는다. Automotive 기능 부재와 바깥 API/권한 예외는 `UNAVAILABLE`로 종료한다. 이 분류와 지연값은 앱 내부 정책이며 실제 Pleos 연결 시간의 관측값이 아니다.

재조회가 완료되면 `MainActivity`가 반환된 차량 값으로 `refresh()`를 호출하여 후보와 선택 가능 여부를 다시 계산한다. 두 번 미연결이거나 재조회가 실패하면 높이·너비 미확인, 연료 `NEEDS_CHECK`를 유지한다. 시연 높이나 비전기차 기본값을 주입하지 않는다. 기존의 정확한 제조사·모델·연식에 한정한 제조사 제원 대체 규칙은 유지하며, 미연결 결과에는 적용하지 않는다.

`onStop`과 `onDestroy`는 각각 `readGeneration`을 증가시키고 세션을 무효화하며 Handler 대기 작업을 제거한다. 새 `onStart`는 새 세션을 시작한다. 진행 중인 이전 읽기가 나중에 반환해도 generation 검사로 화면에 반영하지 않는다. 예약 작업도 세션 유효성을 확인한 뒤에만 읽기를 제출한다. 차량 읽기와 지연 대기는 UI 스레드를 막지 않는다. Asta Sans와 카드 구성은 유지한다.

| 수용 항목 | 추가한 결정적 합성 검증 (실행 통과 증거 아님) |
| --- | --- |
| 1: 첫 미연결 후 최대 1회, 연결 후 치수 누락은 재조회 없음 | `disconnectedThenSuccessAutomaticallyRecalculatesWithHardware`, `connectedWithoutDimensionsAndUnavailableDoNotRetry`, `duplicateFirstResultCannotScheduleAnotherRetry`: 첫 읽기 1회·총 2회, 지연 500ms, 중복 결과/타이머 차단 |
| 2: 성공 시 실제 반환값으로 재판단, 두 실패 시 확인 필요 | `disconnectedThenSuccessAutomaticallyRecalculatesWithHardware`: 가짜 하드웨어 높이 2250mm로 요청 가능 0→1, 대안 A 우선. `twoDisconnectedReadsTerminateUnknownWithoutDemoOrFuelAssumptions`, `retryFailureIsTerminalEvenWhenNotDisconnected`: UNKNOWN으로 종료 |
| 3: 정지·파괴·재진입 뒤 이전 작업 차단 | `stoppedOrDestroyedSessionDiscardsTimersAndLateResults`, `reopenedSessionRejectsOldTimerAndBothOldAttemptResults`: 이전 타이머를 강제 실행하고 이전 결과를 전달해도 추가 읽기·새 화면 상태 변경 없음 |
| 4: 빌드·증거 계약 유지 | 지정 파일 diff 검사. README 아티팩트 이름/해시 계약, CI, 매니페스트 권한, 글꼴 자산, 서명 및 주행 제한은 변경하지 않음 |

위 테스트는 Activity와 공유하는 `VehicleReadResult.Session` 정책에 가짜 읽기 결과와 수동 실행 타이머를 연결한다. 높이·연료는 테스트 입력이며 실제 차량 수신값이 아니다. Android Handler나 실제 Activity 생명주기의 실행 관측을 대신하지 않는다. 근거는 이번 로컬 소스와 테스트 assertion 대조(2026-11-08)다. 기존 외부 API·제원 출처는 README의 확인일과 링크를 따르며 이번에는 외부 재확인이나 네트워크 접근을 하지 않았다.

로컬 검증 시도: `gradle --offline --no-daemon --console=plain :app:testPleosDebugUnitTest :app:assemblePleosDebug`는 `gradle: command not found`로 종료 127이다. 로그: `/tmp/vehicle-service-reconnect-gradle.log`. PATH에서 java·javac·gradle을 찾지 못했고 도구를 설치하지 않았다. 따라서 이번 변경 소스의 JVM 테스트 실행·unsigned APK 빌드 성공은 **미검증이며 수용 항목 4의 실행 기준은 미충족**이다. CI를 호출하지 않았으며 과거 HEAD의 CI 결과를 이번 변경의 증거로 사용하지 않는다. 새 커밋을 만들지 않았으므로 변경 작업 트리에 해당하는 CI 아티팩트도 없다. `git diff --check`와 지정 editPaths 제한 검사는 통과했다.

Windows 에뮬레이터 설치·화면, 실제 Pleos 서비스 연결/속성 콜백, 실차, 음성 및 공개 배포는 실행하지 않았으며 런타임 관측 증거는 없다. 향후 별도 승인된 환경에서는 첫 미연결→연결 성공 시 자동 후보 변경, 연속 미연결 시 확인 필요 종료, 치수 미지원 시 즉시 종료, 재조회 대기/진행 중 화면 종료·재진입 시 이전 결과 차단을 확인해야 한다.
