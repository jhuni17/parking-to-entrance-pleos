# 주차까지 · 시연 리허설

## 첫 APK의 30초 시연

1. 앱을 연다. 방문지는 코엑스몰 시연 입력이다. 차량 높이 1.85 m, 체류 시간 2시간과 구매 할인 조건을 확인한다.
2. 코엑스 카드에서 높이 제한 2.2 m, 기본 예상 12,000원, 대상 영수증 조건 충족 시 예상 0원과 요청 가능한 후보 2곳을 보여준다.
3. 루프박스 프로필을 켠다. 2.25 m가 표시되고 코엑스는 높이 제한 초과로 내려간다. 대안 A(예상 6,000원)가 위로 올라오며 요청 가능한 후보 1곳이 표시된다. 대안 B에는 높이 확인 필요·입구 정보 미확인으로 선택 불가 사유가 표시된다.
4. 대안 A의 입구 경로 요청을 연다. 취소하면 요청이 없다. 다시 열어 확인하면 “시연 어댑터 요청 처리 · 실제 길안내 시작 아님”을 확인한다. 확인창이 닫힌 뒤 “마지막 요청” 영역에서 스타필드 코엑스몰 방문 목적지, 대안 A 입구 (시연 좌표), 37.51400·127.06100과 위 시연 결과를 함께 확인한다.

첫 APK의 실제 동작 범위는 카드 판단, 요금 계산, 순서 변경과 시연 어댑터 요청 처리다. 대안과 좌표는 시연값이다. 위 절차는 이후 에뮬레이터 설치 리허설용이며, 후보 `1b3e2716204f023c2adabdec7d4839be3503b074`의 보고된 CI 성공은 [기획서의 후보별 검증 상태](../contest-brief.md#후보별-검증-상태)에 기록되어 있다. 설치·화면 동작을 관측한 증거는 아니다.

## 실제 NaviHelper 연동 이후

기본 내비에서 목적지를 선택한 화면, 앱의 목적지 수신, 사용자가 선택한 입구, 기본 내비의 경로 요청 결과를 차례로 기록한다. 문서에 있는 API, 시연용 입력, 런타임 관측을 구분한다. 실제 안내에 사용하는 입구는 공개된 시설 자료에서 확인한 좌표를 사용한다.

## 후보 계약 검증 상태 (보고 수령일 2026-11-08)

`ParkingNavigationTest`는 가짜 내비의 연속 목적지 이벤트, 확인 전 요청 0건, 확인의 중복 소비 차단, 거절·예외, 요청 ID가 붙은 동기·지연 입구 되울림과 새 방문지를 검사한다. 시연 어댑터는 실제 내비 수락을 반환하지 않는다. 실패 UI는 “입구 경로 요청 실패”로 분리되어 있으며 현재 화면의 시연 어댑터는 실패를 발생시키지 않는다.

사용자 제공 검토 보고상 위 후보는 Actions `42481652866`에서 `testPleosDebugUnitTest`·`assemblePleosDebug`가 성공했고, 테스트 13건의 실패·오류·건너뜀은 각 0건이다. 이번 작업에서는 직접 재검증하지 않았다. unsigned APK 해시와 제출 단계별 근거 범위는 [검증 상태 표](../contest-brief.md#후보별-검증-상태)를 따른다.

과거 로컬 Gradle 시도는 도구 부재로 실행되지 않았다 (`gradle: command not found`; 당시 Java 컴파일 도구도 PATH에 없음). 이는 역사적 환경 기록이며 후보의 보고된 CI 성공과 구분한다. 문서 정정을 포함한 이후 변경 HEAD는 별도 검증 대상이다. Windows 설치·화면 관측, 실제 NaviHelper 콜백과 실제 신호에 따른 사용자 결과, 차량·음성·음향 시험 및 릴리스 증거는 미확보다. Windows·실차 검증은 별도 승인 범위이며, 요청 ID를 SDK 이벤트에 연결할 수 있는지도 실제 연동 전 확인해야 한다.

## 입구 정보까지 포함한 선택 판단 (2026-11-08 로컬 소스 대조)

`ParkingPlan.Option.requestAvailability()`가 입차 조건 충족과 입구 존재를 함께 판정한다. 정렬·경로 요청·화면 버튼과 선택 불가 사유가 이 판정을 사용한다. 요청 가능한 후보를 먼저 표시하고 그 안에서는 예상 요금, 부설 우선순위를 유지한다. 입구 누락은 입차 금지가 아닌 “입구 정보 미확인”이다. 목록 위에는 요청 가능한 후보 수를 표시하며, 빈 목록은 “시설 정보 없음”, 시설이 있어도 모두 요청 불가이면 “요청 가능한 입구 없음”으로 구분한다. 두 경우 후보 수는 0이고 경로 요청은 없다. 사용자의 확인 이후에만 어댑터에 요청하는 흐름을 유지한다.

| 사례와 근거 | 기대 결과 (화면 관측 아님) |
| --- | --- |
| 기존 `SampleTrip.LOTS`: 1.85 m·비전기차·120분·할인 충족 | 입구 시연 좌표가 있는 코엑스 예상 0원 우선, 요청 가능한 후보 2곳 |
| 기존 시연에서 높이만 2.25 m로 변경 | 대안 A 예상 6,000원 우선, 요청 가능한 후보 1곳. 코엑스는 높이 제한 초과로 선택 불가. B는 높이 확인 필요·입구 정보 미확인 |
| `ParkingPlanTest.entranceReadyLotPrecedesFreeLotWithoutEntrance`: 합성 테스트 전용 시설 두 곳 | 입차 조건 충족·입구 있음·6,000원이 입차 조건 충족·입구 없음·0원보다 먼저. 후자는 입구 정보 미확인으로 요청 없음 |
| `ParkingPlanTest.allUnavailableLotsHaveNoRequestableEntrance`: 합성 테스트 전용 제한·미확인·입구 누락 시설 | 요청 가능한 후보 0곳, 요청 가능한 입구 없음, 모든 경로 요청 비어 있음 |
| `ParkingPlanTest.emptyLotsHaveNoFacilityInformation`: 합성 테스트 전용 빈 목록 | 요청 가능한 후보 0곳, 시설 정보 없음, 경로 요청 없음 |

새 합성 사례는 앱의 기존 코엑스·루프박스 시연 데이터에 추가하지 않았다. 기존 높이 경계값·전기차 정책 미확인과 명시적 금지·할인 테스트를 유지한다. 위 기대 결과는 소스와 테스트 assertion 대조이며 실행 통과, 설치 화면 관측, 실제 SDK 신호 또는 사용자 효과 실측의 증거가 아니다. 실제 Pleos 신호에 따른 사용자 효과는 미관측이다.

이번 변경 작업 트리의 로컬 검증: `gradle --offline --no-daemon --console=plain :app:testPleosDebugUnitTest :app:assemblePleosDebug`를 시도했으나 `gradle: command not found`(종료 127)로 미실행이다. PATH에서 `java`·`javac`도 확인되지 않았다. 도구 설치나 의존성 변경은 하지 않았다. 이번 소스에 대응하는 테스트 XML·unsigned APK가 없어 테스트 실패·오류 0건 및 빌드 성공 기준은 미충족이다. 과거 후보의 CI·XML·APK를 이번 결과로 대체하지 않는다. `git diff --check`는 통과했고 기준 `b8af43df19b58ff22e41c92f1a610eb3cdfe9dab` 대비 변경은 지정 다섯 파일에 한정한다. Windows 에뮬레이터 설치·화면, 실제 SDK 콜백·실차·사용자 효과는 여전히 미관측이다.

## 차량·방문 입력 복원 (2026-11-08 소스 대조)

화면 재생성 뒤 루프박스·전기차·구매 할인·체류 시간을 다시 입력하지 않아도 같은 주차 판단을 유지하도록 구현했다. `DemoProfile`은 Android 비의존 입력 상태와 네 저장 키를 정의한다. `MainActivity`는 `onSaveInstanceState`에서 네 값을 저장하고, `onCreate`에서 위젯과 첫 후보 계산 전에 복원한다. 체크박스 표시, 시간 버튼 문구와 `navigation.options`는 같은 복원 상태를 사용한다. 선택·확인 대화상자·경로 요청 결과는 저장하지 않으며 복원으로 요청을 자동 재전송하지 않는다.

| 재생성 전 입력 → 복원 후 동일 입력 | 전후 동일해야 할 기대 결과 (화면 관측 아님) | JVM assertion |
| --- | --- | --- |
| 저장값 없음: 1.85 m·비전기차·할인 적용·120분 | 기존 최초 시작값 유지, 코엑스 예상 0원 우선 | `missingStateUsesOriginalFirstStartDefaults` |
| 루프박스·전기차·할인 각 참/거짓 × 60/120분, 총 16조합 | 네 값과 `ParkingPlan.compare`의 전체 결과가 왕복 전후 동일 | `allSixteenInputCombinationsRoundTripWithSamePlan` |
| 2.25 m·비전기차·할인 적용·120분 | 코엑스 RESTRICTED·경로 요청 생성 불가, 대안 A 예상 6,000원 첫 후보 | `restoredRoofBoxKeepsCoexRestrictedAndAlternativeFirst` |
| 1.85 m·전기차·할인 적용·120분 | 코엑스 전기차 조건 NEEDS_CHECK·경로 요청 생성 불가 | `restoredElectricProfileKeepsUnknownPolicyUnrequestable` |
| 1.85 m·비전기차·할인 해제·60분 | 코엑스 예상 6,000원, 대안 A 예상 3,000원 | `restoredNoDiscountAndOneHourKeepFullQuotes` |

근거: `DemoProfileTest.java`의 새 assertion과 기존 `SampleTrip.LOTS`를 2026-11-08 로컬에서 대조했다. 시설 수치의 출처·확인일은 기존 `SampleTrip.java` 표기를 따르며 외부 재확인은 하지 않았다. 대안 A와 입구 좌표는 시연값, 코엑스 전기차 조건은 미확인이다. 테스트는 Map 저장·복원과 주차 판단을 합성 검증하도록 작성했으며 Android Bundle·생명주기·위젯 실행 검증을 대신하지 않는다.

실제 재생성 리허설은 미관측이다. 향후 별도 승인된 Windows 환경에서 각 사례를 입력하고 화면 재생성 전후 체크박스·시간 문구·후보·요금을 비교하며, 확인 대화상자와 요청 결과가 복원되지 않고 자동 요청도 없는지 관측해야 한다. Android 화면 재생성·실제 Pleos 신호·사용자 효과를 이번 작업에서 관측했다고 주장하지 않는다.

이번 profile-restore 작업의 로컬 실행: `gradle --offline --no-daemon --console=plain :app:testPleosDebugUnitTest :app:assemblePleosDebug`는 `gradle: command not found`(종료 127)로 미실행이다. PATH의 `java`·`javac`도 없다. 설치·의존성 변경은 하지 않았다. 변경 소스에 대응하는 실행 테스트 XML·unsigned APK가 없으므로 실패·오류·건너뜀 0건과 빌드 성공이라는 수용 기준 6은 미충족이다. 과거 후보의 XML·APK·CI 보고를 이번 실행 증거로 사용하지 않는다. `git diff --check`는 통과했다. 기준 `589948ea1ffe72992434f36c9f264cfa5834d703` 대비 추적 파일 diff와 신규 파일 목록을 합쳐 지정 다섯 파일만 변경했으며 주행 제한·서명·권한·의존성·워크플로·README 아티팩트 계약은 변경하지 않았다.

## 마지막 입구 요청 기록 (route-receipt · 2026-11-08 소스 대조)

확인창을 닫은 뒤에도 후보 목록 밖의 “마지막 요청 · 과거 확인 기록 (현재 후보 판단과 별개)” 영역에서 방문지·입구 이름·좌표·처리 결과를 함께 확인하도록 했다. 초기 상태와 선택/취소만 한 상태에는 “마지막 요청 없음”이며 호출은 0건이다. 2.25 m·비전기차·120분·할인 적용으로 대안 A를 확인하면 기대 표시는 방문 목적지 **스타필드 코엑스몰**, 차량 도착점 **대안 A 입구 (시연 좌표)**, 좌표 **37.51400, 127.06100**, **시연 어댑터 요청 처리 · 실제 길안내 시작 아님**이다. 이 값은 `SampleTrip`의 시연값과 대조했으며 실제 시설 입구나 화면 관측의 주장이 아니다.

일반 갱신·차량 조건 변경 후에도 같은 과거 기록이 남는다. 현재 추천에 대한 성공 표시로 해석하지 않는다. 새 방문지를 받으면 기록을 비우며 입구 되울림은 원래 방문지와 기록을 유지한다. Activity 재생성에서는 입력 네 값만 복원하고 마지막 요청은 복원·재전송하지 않는다. 승인된 향후 화면 리허설에서 확인창이 닫힌 뒤 위 네 항목, 조건 변경 후 과거 기록 문구, 재생성 후 기록 없음을 확인해야 한다.

화면의 기본 어댑터는 `SIMULATED`만 반환한다. `ACCEPTED`의 “내비 요청 수락”과 `REJECTED`의 “입구 경로 요청 실패”는 별도 결과 문구다. 거절·예외는 fake 계약 테스트의 검증 대상으로, 앱 화면에서 실패나 실제 SDK 수락을 관측했다는 뜻이 아니다.

| 수용 항목 | 이번 소스의 검사 근거 (실행 결과 아님) |
| --- | --- |
| 1·2: 확인 전 기록 없음·0건, 대안 A 확인 후 동일 요청/결과·1건 | `simulatedAlternativeReceiptStartsEmptyAndSurvivesConditionChanges`: options → select → confirm, 방문지·입구·두 좌표·SIMULATED assertion |
| 3: 거절·예외로 교체, 중복 확인 불변 | `rejectionReplacesPreviousReceiptAndDuplicateDoesNothing`, `exceptionReplacesPreviousReceiptAndDuplicateDoesNothing`: 이전 ACCEPTED와 다른 새 요청·REJECTED, 2건 유지·기록 객체 유지 assertion |
| 4: 되울림 유지·새 방문지 초기화·호출 중 방문지 교체 | `echoesKeepReceiptAndNewVisitClearsIt`, `visitDuringRequestCannotReceiveOldOutcome`: 방문지·기록·상태·요청 횟수 assertion. `receive`는 되울림 검사 뒤 초기화하고 `confirm`은 어댑터 반환/예외 처리 뒤 기존 generation 검사 안에서만 기록한다. |
| 5: 같은 기록에서 외부 표시·복원 제외 | `MainActivity.refresh`가 한 Receipt에서 요청과 결과를 읽는다. 체크박스·시간 리스너의 refresh는 기록을 지우지 않는다. Bundle 저장/복원은 기존 DemoProfile 네 키뿐이다. |
| 6: 리허설·기획서 기대값 일치 | 두 문서의 이 절은 같은 SampleTrip 시연값과 위 테스트를 기준으로 한다. |

로컬 실행 증거: 요청된 `gradle --offline --no-daemon --console=plain :app:testPleosDebugUnitTest :app:assemblePleosDebug`는 `gradle: command not found`로 종료 127이다. 로그는 `/tmp/route-receipt-gradle.log`에 있다. PATH에서 java·javac도 찾지 못했다. 설치하지 않았으며 이번 소스의 실행 XML·unsigned APK가 없으므로 수용 항목 7(테스트 성공·실패/오류/건너뜀 0·빌드 성공)은 **미충족**이다. 과거 CI는 대체 증거가 아니다.

수용 항목 8의 로컬 diff 검사: `git diff --check` 통과. 기준 `d7e43092e48d140d5fb363f2d69ae4ef73291c53` 대비 변경은 ParkingNavigation.java·MainActivity.java·ParkingNavigationTest.java·showcase/REHEARSAL.md·contest-brief.md 다섯 파일이며 신규 파일은 없다. 주행 제한·서명·권한·의존성·워크플로·자격 증명·배포·README 아티팩트 계약은 변경하지 않았다. Windows 에뮬레이터 설치/화면·실제 Pleos 신호/SDK 콜백·실차·음성/음향·사용자 효과는 미관측이다. 이번 변경은 로컬 소스 대조이며 런타임 증거 확보나 최종 시연 준비 완료를 뜻하지 않는다.
