# 주차까지 · 시연 리허설

## 상세에서 차량 정보 다시 확인 (2026-11-09 소스 대조)

현행 절차: **최초 조회 종료(미확인 포함) → 시설 상세 → 차량 정보 다시 확인 → 새 결과에 따른 추천 갱신 → 상세에서 입구 선택·확인**. 조회 중에는 기존 ‘차량 정보 확인 중’ 표시와 카드 숨김을 사용한다. 차량값·출처는 상세에, 대안 이유는 최대 한 줄로 유지하며 원본 차량값 입력 UI는 없다. 상세 재조회는 Activity를 재시작하지 않는다. 같은 방문의 체류 시간·구매 할인 가정·추천 기준·사용자 제외·직접 확인 총높이를 유지한다. 같은 Activity의 stop→onStart 복귀는 확인 총높이를 유지하고 이전 입력창 토큰만 만료시킨다. 현재 토큰의 명시적 삭제는 보완값을 지우며 새 Activity는 미입력으로 시작한다. 새 방문은 할인·제외를 초기화하는 기존 정책을 유지한다.

다음은 `VehicleRefreshStateTest`의 **합성 AAOS 기대값**이다. 실제 SDK 콜백 관측이나 실행 통과 기록이 아니다. `VehicleProfile.fromHardware` → 공유 `VehicleRefreshState` → 기존 후보 계산·설명 → `EntranceConfirmation` → fake 내비를 연결한다. 공통 조건은 동일 방문·120분·구매 할인 충족 가정·제외 없음이며, 부설 우선과 요금 우선 모두 검사한다.

| 재조회 입력·보완 | 기대 결과 |
| --- | --- |
| 최초 UNKNOWN 종료 → 1850mm·NON_ELECTRIC, 보완 없음 | 요청 가능 0→2곳, 코엑스 예상 0원 우선. 방문지·요금 가정 유지 |
| 2250mm·NON_ELECTRIC | 코엑스 RESTRICTED, 대안 A 6,000원 우선·요청 가능 1곳 |
| 1850mm·ELECTRIC | 코엑스 NEEDS_CHECK, 대안 A 6,000원 우선·1곳. 미확인은 명시적 제한과 구분 |
| 사용자 확인 총높이 2250mm + 원본 1850mm·NON_ELECTRIC | 원본 1850mm·적용 2250mm, 코엑스 제한 유지·대안 A 6,000원·1곳 |
| 코엑스 제외 후 정상 1850mm·NON_ELECTRIC 재조회 | 제외 보존, 대안 A 6,000원 우선 |

시작 즉시 이전 Selection은 STALE이다. 시작·결과 수용·재계산·설명·새 선택까지 요청 0건이며, 2250mm 결과의 새 대안 A 확인만 원래 방문지와 시연 좌표 37.51400, 127.06100으로 정확히 1건 요청한다. 중복 확인은 추가 요청하지 않고 REJECTED·예외는 실패다. 시설 외부 사실은 기존 SampleTrip 출처·확인일을 유지하며 이번에 외부 재확인은 하지 않았다. 위 차량 입력과 대안·좌표는 합성/시연값이고 할인은 가정이다.

사용자 재조회는 **종료 후 명시적으로 시작하는 새 조회**다. 기존 자동 재시도는 각 조회의 첫 미연결 결과에만 500ms 뒤 최대 한 번이며 시작부터 3000ms 마감을 연장하지 않는다. 진행 중 반복 시작은 세대·마감·읽기 예약을 늘리지 않는다. 만료는 UNKNOWN으로 종료하며 이전 세대 결과·재시도·만료 및 화면 종료 후 콜백은 무시한다. 단일 실행기와 AndroidVehicleInfo.read를 유지한다. 이 정책은 막힌 API 호출의 강제 종료, 실행기 해제 또는 실제 서비스 회복을 보장하지 않는다.

로컬 PATH에서 Java·javac·Gradle을 찾지 못했고 Android SDK도 확인되지 않아 `testPleosDebugUnitTest`·`assemblePleosDebug`는 미실행이다. 소스/assertion 대조와 작업 트리 `git diff --check`는 실행 테스트 증거가 아니다. 커밋·푸시·네트워크·자격 증명·CI 변경·도구 설치는 하지 않는다. Oracle의 새 후보 커밋 후 기존 GitHub Actions에서 **새 전체 SHA**의 source-sha.txt 및 pleos-build-<full SHA> 일치, 기존·신규 XML의 실패·오류·건너뜀 모두 0, Gradle 로그·입력 해시·unsigned APK를 확인해야 한다. README의 `sha256sum -c apk-sha256.txt` 및 동일 SHA 체크아웃의 `sha256sum -c /path/to/input-sha256.txt`, 기존 패키지·ZIP·unsigned 검사도 대기다. 기준 `076c54f109f664c45ba3a33089efa364913ff7a0` 대비 새 HEAD 전체 diff·공백 검사·5개 editPaths 제한은 새 커밋에서 확인해야 하며 기준 후보 성공으로 대체하지 않는다.

Windows 설치·화면·실제 SDK 콜백·차량 신호·실차·음성·음향 증거는 없다. 합성 검증 설계·unsigned CI·실제 런타임·제출 완료를 구분한다. 1차 필수 제안서·서명 동의서, 선택 프로토타입·적격 GitHub/APK, 최종 PT 필수 작동 앱 시연은 별도 준비 대상이며 이번 구현은 제출 완료를 뜻하지 않는다.


## 현재 Android 후보

첫 화면에 목적지, 주차장 이름, 입력한 분 기준 예상 요금과 입구 안내를 확인한다. 화면에는 Asta Sans, 밝은 회색 바탕과 짙은 주요 버튼을 사용한다. 차량 높이와 연료 유형은 Android Automotive 서비스에서 읽으며, 원본 차량 속성의 수동 설정은 없으며 상세에서 사용자 확인 총높이를 보완할 수 있다. 높이를 확인하지 못한 경우에는 안내 가능한 주차장을 주장하지 않는다. 요금 가정과 차량 제원 출처는 상세에서 확인한다.

실제 런타임 리허설에서는 AAOS 속성으로 차종과 연료 유형이 수신되는지, 외부 치수를 지원하는지, 높이 제한과 전기차 정책에 따라 예상한 후보가 뜨는지를 확인한다. 목적지와 주차장 대안은 시연값이다. 아래 이전 단계 기록은 당시의 합성 시나리오다.

## 현재 앱 리허설 절차 (소스 대조일 2026-11-09)

이 절은 향후 화면 리허설 대본이며 실행 관측 기록이 아니다. 원본 차량 속성 입력 단계는 없으며, 총높이 보완 절차는 아래 현행 절을 따른다.

1. 앱을 연다. `onCreate`는 시연 목적지 **스타필드 코엑스몰**을 설정한다. 최초 요금 조건은 120분·구매 할인 미적용이다. `onStart` 조회 동안 “차량 정보 확인 중”을 기다린다.
2. 조회 결과를 확인한다. 종료 후 **“차량 높이 확인 필요” → 입구 요청 보류**로 시작한다. **루프박스·적재물을 포함해 직접 확인한 총높이가 있을 때만** 후보 행을 눌러 **시설 상세 → “총높이 보완” → 확인값 입력 → “적용” → 후보 재판정**으로 진행한다. 적용 후 상세가 닫히고 목록이 갱신되면 **요청 가능한 입구만 새로 선택**해 3~4단계로 진행한다(`showDetails/showTotalHeight/refresh/confirmPreview`). 확인값이 없거나 재판정 후 요청 가능 후보가 없으면 요청 보류로 끝낸다. 높이는 있지만 후보가 없으면 “입구 안내 가능한 주차장 없음”, 빈 시설 목록은 “시설 정보 없음”으로 구분한다. “취소”·잘못된 입력은 기존 보완값을 유지하며, “보완값 삭제”는 원본 높이 판단으로 돌아가 재판정한다. 원본 높이도 없으면 다시 요청 보류다. 입력 정책과 출처 구분은 [총높이 보완 현행 절차](#총높이-보완--현행-절차-소스-대조일-2026-11-09), 수치 대조는 아래 [높이 미확인 복구 합성 예시](#높이-미확인-복구-합성-예시-소스assertion-대조일-2026-11-09)를 따른다. 후보 수 숫자는 화면에 표시되지 않는다.
3. 추천 카드의 **“상세”** 또는 다른 후보 행을 누른다. 상세에서 입구 높이 제한·차량 높이·연료 유형·높이 출처·판정 사유·자료 출처를 읽는다. **“요금 조건”**에서 현재 값이 표시된 **체류 시간(분) 숫자 입력(정수 1~360)**, **“구매 할인 조건 충족 가정”**을 확인하고 **“적용”**으로 반영하거나 **“취소”**한다. 잘못된 입력은 오류를 표시하고 창과 기존 시간·할인을 유지하며 취소는 변경하지 않는다. 할인은 실제 영수증 검증이 아닌 가정이다(`showDetails/showQuoteSettings`).
4. 요청 가능한 경우만 추천 카드의 **“입구 안내 ↗”** 또는 상세의 **“입구 안내”**를 누른다. **“이 입구로 안내할까요?”** 확인창에 원래 방문지·선택 주차장·입구·체류 분별 예상 요금·구매 할인 가정의 적용 범위와 “시연 경로 요청”이 나온다. 추천 카드와 시설 상세 모두 같은 `EntranceConfirmation`을 사용하며 표시한 모델의 Selection으로 확인한다. **“취소”**하면 요청하지 않는다. 다시 선택해 **“시연 요청”**을 누르면 선택한 입구로 한 번 요청하고 결과 **“시연 요청 완료”**를 기대한다. 조건이 바뀐 선택은 “조건이 바뀌었습니다. 입구를 다시 선택하세요.”로 처리한다(`confirmPreview`).
5. 확인창이 닫힌 뒤 목록 밖 **입구 이름 · 결과 요약**을 누른다. **“마지막 요청”** 상세의 방문지·입구·좌표·결과를 확인하고 **“닫기”**를 누른다(`refresh/showReceipt/RequestRecheck`). 기록이 없으면 이 요약은 숨겨진다.

해설: 실제 길안내 시작이 아니라 시연 어댑터 요청 처리다. 차량 조건별 예상 요금·순서는 아래 공통 표를 참고하며 해당 차량 신호가 실제 수신되었다고 가정하지 않는다. 후보 CI 보고와 Windows 화면 관측은 별개다. 제출 단계는 [제안서의 제출 단계와 요구 자료](../contest-brief.md#제출-단계와-요구-자료)를 따른다. 1차는 제안서·서명 동의서 필수, 적격 GitHub/APK·프로토타입 선택이며 최종 PT는 작동 앱 시연 필수다.


### 입구 요청 직전 선택 확인 (2026-11-09 소스 대조·합성 검증 설계)

추천 카드 **입구 안내 ↗** 또는 시설 상세 **입구 안내** → **이 입구로 안내할까요?**에서 방문지·선택 주차장·입구·체류 시간(분)·예상 요금·할인 가정의 범위를 함께 읽는다. **취소**는 요청하지 않는다. **시연 요청**만 표시된 모델의 Selection을 확인하며 현재 어댑터 결과는 **시연 요청 완료**다. 거절·예외는 실패, 중복 확인과 조건 변경 후 확인은 STALE이다. 체류 시간·할인·추천 기준·차량 조건 변경 시 `refresh`가 선택을 무효화하고, 새 방문지도 이전 선택을 만료시킨다. 열린 확인의 표시값 자체는 바뀌지 않으므로 다시 선택해야 한다.

`EntranceConfirmation.create`는 전달된 Option을 `navigation.select`로 선택한다. 선택·시설·체류 시간·구매 할인 가정·계산된 예상 요금을 불변 상태로 묶고 방문지와 입구는 Selection.request에서 가져온다. 요금·적합성·정렬을 다시 계산하지 않는다. 할인은 실제 구매 검증이 아닌 가정이며, 규칙 있는 선택 시설에만 적용한다. 대안 A에는 구매 할인 규칙이 없어 가정이 켜져도 할인 적용이나 무료로 표시하지 않는다. 요금 null은 **요금 미확인**으로 표시한다. 첫 화면에 설정·차량값·출처를 추가하지 않으며 대안 이유의 최대 한 줄을 유지한다.

다음은 `EntranceConfirmationTest`의 `fromHardware`부터 후보 계산·선택·확인 문구·fake 요청까지 연결한 **합성 AAOS 입력의 assertion 기대값**이다. 실행 통과나 실제 신호 관측 기록이 아니다. 화면에는 원본 차량값을 1850→2250mm로 바꾸는 조작이 없다. 기존 SampleTrip의 외부 출처·확인일을 유지하며 이번에 외부 재확인은 하지 않았다.

| 합성 조건·선택 | 확인 표시·요청 기대값 |
| --- | --- |
| 1850mm·NON_ELECTRIC·120분·구매 할인 가정, 코엑스 선택 | 방문지 스타필드 코엑스몰 / 코엑스 지하주차장 / 코엑스 입구 (시연 좌표) / 120분 예상 0원 / 선택 시설의 구매 할인 조건 충족 가정 |
| 높이만 2250mm로 변경, 새 대안 A 선택 | 코엑스 RESTRICTED·선택 불가. 대안 A · 시연 주차장 / 대안 A 입구 (시연 좌표) / 120분 예상 6,000원 / 적용할 구매 할인 규칙 없음 |
| 1850mm·120분·할인 미적용·부설 우선 목록의 두 번째 대안 A 선택 | 코엑스 12,000원이 아니라 선택한 대안 A 6,000원. 60분이면 3,000원. 요금 누락 합성 시설은 요금 미확인 |
| 계산·모델 생성·문구 생성·취소 / 유효한 대안 A 확인 | 확인 전 요청 0건. 확인 시 원래 방문지와 37.51400, 127.06100으로 정확히 1건; 중복 확인은 추가 요청 없음. 되울림은 방문지 보존, 거절·예외는 실패 receipt |

신규 테스트 5건은 기존 `app/src/test/java` JVM 경로에 포함한다. 높이 전환, 비첫 후보·시간별 요금·미확인, 네 조건별 refresh 대응 만료·불변 표시·새 방문지, 요청 1회·되울림, 거절·예외를 검사한다. 기존 `ParkingNavigationTest`도 유지한다. 취소의 실제 Android 리스너는 `setNegativeButton("취소", null)`로 소스 대조하며 JVM의 취소 검사는 확인 없이 모델을 버리는 동작이다.

로컬 PATH에 Java·javac·Gradle이 없고 Android SDK도 확인되지 않아 `testPleosDebugUnitTest`·`assemblePleosDebug`는 미실행이다. 도구 설치·네트워크·자격 증명·커밋·푸시·CI 호출은 하지 않았다. Oracle의 새 커밋 후 기존 GitHub Actions에서 새 전체 SHA의 XML(기존·신규 실패·오류·건너뜀 모두 0), Gradle 로그, 입력 해시와 unsigned APK를 검증해야 한다. `source-sha.txt`·`pleos-build-<full SHA>` 일치, README의 두 `sha256sum` 검사와 기존 APK 패키지·ZIP·unsigned 검사는 대기다. 기준 후보의 성공으로 대신하지 않는다. 기준 `81a430e8d92549ceb9ad7ed97929224decef2cdd` 대비 지정 5개 파일의 작업 트리 diff를 검사하며 새 커밋 HEAD 범위 검사는 대기다.

합성 검증 설계·unsigned CI·실제 런타임·제출 완료는 별개다. Windows 설치·화면, 실제 SDK 콜백·차량 신호·실차·음성·음향 증거는 미확보다. Windows·실차·공개 배포는 별도 승인 범위다. 1차 필수 제안서·서명 동의서 확보, 선택 프로토타입·적격 GitHub/APK, 최종 PT 필수 작동 앱 시연은 별도 준비 대상이다.

## 실제 NaviHelper 연동 이후

기본 내비에서 목적지를 선택한 화면, 앱의 목적지 수신, 사용자가 선택한 입구, 기본 내비의 경로 요청 결과를 차례로 기록한다. 문서에 있는 API, 시연용 입력, 런타임 관측을 구분한다. 실제 안내에 사용하는 입구는 공개된 시설 자료에서 확인한 좌표를 사용한다.

## 후보 계약 검증 상태 (보고 수령일 2026-11-08)

`ParkingNavigationTest`는 가짜 내비의 연속 목적지 이벤트, 확인 전 요청 0건, 확인의 중복 소비 차단, 거절·예외, 요청 ID가 붙은 동기·지연 입구 되울림과 새 방문지를 검사한다. 시연 어댑터는 실제 내비 수락을 반환하지 않는다. 실패 UI는 “입구 경로 요청 실패”로 분리되어 있으며 현재 화면의 시연 어댑터는 실패를 발생시키지 않는다.

사용자 제공 검토 보고상 위 후보는 Actions `42481652866`에서 `testPleosDebugUnitTest`·`assemblePleosDebug`가 성공했고, 테스트 13건의 실패·오류·건너뜀은 각 0건이다. 이번 작업에서는 직접 재검증하지 않았다. unsigned APK 해시와 제출 단계별 근거 범위는 [검증 상태 표](../contest-brief.md#후보별-검증-상태)를 따른다.

과거 로컬 Gradle 시도는 도구 부재로 실행되지 않았다 (`gradle: command not found`; 당시 Java 컴파일 도구도 PATH에 없음). 이는 역사적 환경 기록이며 후보의 보고된 CI 성공과 구분한다. 문서 정정을 포함한 이후 변경 HEAD는 별도 검증 대상이다. Windows 설치·화면 관측, 실제 NaviHelper 콜백과 실제 신호에 따른 사용자 결과, 차량·음성·음향 시험 및 릴리스 증거는 미확보다. Windows·실차 검증은 별도 승인 범위이며, 요청 ID를 SDK 이벤트에 연결할 수 있는지도 실제 연동 전 확인해야 한다.

## 현재 판단·조건별 기대 결과 (소스 대조일 2026-11-08)

최초 시작은 **120분·구매 할인 미적용**이다. 차량은 `onStart`에서 UNKNOWN으로 초기화한 뒤 AAOS에서 다시 조회한다. 고정 차량 높이나 비전기차 기본값을 넣지 않는다. `ParkingPlan.compare`의 기본 정렬은 요청 가능 여부 → Fit → 부설 우선 → 예상 요금 순이다. 상세의 추천 기준에서 요금 우선을 고르면 요청 가능한 후보만 예상 요금 오름차순(미확인 마지막), 동률 부설 우선, 나머지 동률 입력 순서로 정렬한다. 요청 불가 후보의 판정과 기존 순서는 유지한다. 입구 누락은 명시적 입차 금지가 아니라 “입구 정보 미확인”이다. `candidateSummary`의 후보 수·빈 목록 문구는 JVM 테스트 대상이며 현재 `MainActivity.refresh`는 이를 화면에 표시하지 않는다. 화면 상태는 조회 중 “차량 정보 확인 중”, 조회 종료 후 높이 누락이면 “차량 높이 확인 필요”, 높이는 있지만 요청 가능 0건이면 “입구 안내 가능한 주차장 없음”이다.

아래 표는 두 문서에 동일하게 적용한다. 합성 행의 수치는 기존 입력·요금식·assertion을 대조한 계산 결과이며 화면의 후보 수 표시나 실제 차량 신호 관측을 뜻하지 않는다. 원본 차량 속성·연료를 바꾸는 수동 조작은 없다. 현재는 상세에서 사용자 확인 총높이를 별도로 보완할 수 있다. 높이 미확인 복구의 고정 조건·금액·요청 수 대조는 아래 **높이 미확인 복구 합성 예시**와 **총높이 보완 — 현행 절차**를 함께 따른다.

| 구분·조건 | 기대 결과 | 기존 소스·테스트 근거 |
| --- | --- | --- |
| 현재 화면 조작: 최초 시작 → 조회 종료, 높이 미확인 | “차량 높이 확인 필요” → 요청 보류. 직접 확인한 총높이가 있을 때만 시설 상세 → 총높이 보완 → 적용 → 후보 재판정 → 요청 가능한 입구만 새로 선택. 확인값이 없거나 후보가 없으면 요청 보류. 취소·잘못된 입력은 기존값 유지, 보완값 삭제는 원본 높이 판단 복귀 | `MainActivity.onCreate/onStart/showDetails/showTotalHeight/refresh/confirmPreview`, `VehicleHeightTest.inputPolicyInvalidAndCancelledInputDoNotMutateAndSessionsExpire` |
| JVM 합성: 높이 미확인·NON_ELECTRIC·120분·할인 미적용 | 요청 가능 0건; 높이 확인 필요로 요청 차단 | `VehicleProfileTest.missingOrInvalidDimensionsCannotCreateARequestableOption`, `VehicleReadResultTest.twoDisconnectedReadsTerminateUnknownWithoutDemoOrFuelAssumptions` |
| JVM 합성: 1850mm·NON_ELECTRIC·120분·할인 미적용 | 코엑스 12,000원이 대안 A 6,000원보다 먼저; 요청 가능 2건. 부설 우선 정렬 | `ParkingPlanTest.noPurchaseConditionShowsFullPriceAndKeepsUsableOnSiteOptionFirst`, `ParkingPlan.compare`, `SampleTrip.LOTS` 요금식 |
| JVM 합성: 1850mm·NON_ELECTRIC·120분·할인 적용 | 코엑스 예상 0원 우선, 기본 요금 12,000원; 요청 가능 2건 | `ParkingPlanTest.purchaseConditionMakesAttachedLotFirst`, `SampleTrip.LOTS` |
| JVM 합성: 2250mm·NON_ELECTRIC·120분·할인 미적용 | 대안 A 6,000원 우선, 요청 가능 1건; 코엑스 높이 제한 초과로 요청 불가 | `VehicleProfileTest.hardwareHeightAndWidthOverrideCatalogAndTallVehicleUsesAlternative`, `VehicleReadResultTest.disconnectedThenSuccessAutomaticallyRecalculatesWithHardware`, `ParkingPlan.evaluate`, `SampleTrip.LOTS` |
| JVM 합성: 1850mm·ELECTRIC·120분·할인 미적용 | 대안 A 6,000원 우선, 요청 가능 1건; 코엑스 전기차 정책 미확인으로 요청 불가(명시적 금지와 구별) | `VehicleProfileTest.electricAndAmbiguousFuelDoNotBypassUnknownEvPolicy`, `ParkingPlanTest.unknownEvPolicyAndEvBanAreDifferent`, `ParkingPlan.evaluate`, `SampleTrip.LOTS` |
| JVM 합성: 입구 없는 무료 시설·입구 있는 유료 시설 / 모두 요청 불가 / 빈 목록 | 각각 입구 있는 시설 우선·1건 / 0건 / 0건; 후보 요약은 테스트 결과용 문구 | `ParkingPlanTest.entranceReadyLotPrecedesFreeLotWithoutEntrance`, `allUnavailableLotsHaveNoRequestableEntrance`, `emptyLotsHaveNoFacilityInformation` |
| 현재 화면 조작: 가능한 입구 선택 → 취소 또는 “시연 요청” | 확인 전 요청 0건; 취소는 전송 없음. 확인 후 선택 좌표로 1회, “시연 요청 완료” | `MainActivity.confirmPreview`, `RequestRecheck.outcomeText`, `ParkingNavigationTest.confirmationAloneSendsExactlyOnceWithChosenCoordinates`, `SampleTrip.SimulatedNavigation` |
| 향후 실제 AAOS 관측: 조회·재조회·재생성·요청 결과 | 실제 반환 높이·연료와 위 판단의 일치, 오래된 조회 배제, 기록 초기화 등을 관측해야 함. 현재 미관측 | `MainActivity.onStart/onStop/onSaveInstanceState`, `VehicleReadResultTest`의 세션 합성 검사; 실제 Handler·Activity·SDK 관측의 대체 증거 아님 |

시설 공개값의 출처·확인일은 `SampleTrip.java`의 코엑스 공식 높이·요금·할인 안내(2026-11-08 확인)를 따른다. 이번에는 외부 재확인하지 않았다. 입구 좌표와 대안 A는 시연값이며 대안 B는 높이·입구 정보가 누락되어 요청할 수 없다. 합성 assertion 대조는 이번 HEAD의 테스트 실행 성공을 뜻하지 않는다.

## 현재 요청 기록과 재생성

`MainActivity.refresh`는 요청 전에는 기록을 숨긴다. 확인 후에는 목록 밖에 **입구 이름 · 시연 요청 완료** 요약을 표시한다. 이 요약을 누르면 `showReceipt`의 **“마지막 요청”** 상세에서 방문지·입구·좌표·결과를 확인한다. 예를 들어 대안 A 요청은 스타필드 코엑스몰 / 대안 A 입구 (시연 좌표) / 37.51400, 127.06100 / 시연 요청 완료다. 코엑스 입구의 시연 좌표는 37.51200, 127.06000이다. 해설: 시연 어댑터 처리이며 실제 길안내 시작이 아니다. 이 해설은 화면 결과 문구가 아니다.

요청 후 현재 조건은 `RequestRecheck` 공유 모델로 별도 표시한다. 같은 Lot 객체의 현재 `requestAvailability`만 읽으며 요금·적합성을 다시 계산하거나 내비를 호출하지 않는다. 재확인이 필요하면 기존 요약 자리에 **이전 요청 · 입구 재확인 필요**를 표시하고, 실패 기록이면 **입구 경로 요청 실패**도 붙인다. 상세는 **당시 요청 결과**와 **현재 요청 가능 여부·현재 사유**, 현재 차량값·출처 및 시설 출처를 구분한다. 조회 중에는 **차량 정보 확인 중**으로 판단을 유보하고, 시설 누락은 미확인, 제외는 **이번 방문 제외**로 표시한다. `refresh`는 열린 요청 상세를 닫는다. 자동 재요청이나 경로 취소는 수행하지 않는다.

요청 후 높이 변화 사례(소스·합성 검증 설계 대조, 2026-11-09): `RequestRecheckTest`는 합성 `fromHardware` 원본 차량값 1850mm·NON_ELECTRIC·120분·구매 할인 가정으로 코엑스 0원 입구를 확인한다(총 요청 1건). 원본 높이를 2250mm로 갱신하면 부설 우선·요금 우선 모두 이전 코엑스는 RESTRICTED·입구 재확인 필요이고 대안 A는 6,000원·요청 가능 1곳이다. 과거 ACCEPTED 또는 SIMULATED 결과는 그대로이며 대안 선택 모델 생성까지 총 요청은 1건이다. 새 대안 A를 명시적으로 확인한 뒤에만 원래 방문지 / 37.51400, 127.06100으로 두 번째 요청이 발생한다(1→2건). 중복 확인은 추가 요청하지 않는다. 높이 누락은 차량 높이 확인 필요, 1850mm·ELECTRIC은 전기차 입차 조건 확인 필요이며 명시적 입차 금지와 구별한다.

향후 화면 리허설에서는 기존 **총높이 보완**으로 1850→2250mm 판단을 만들어 위 요약·상세·대안 확인 전후 요청 수를 관측할 예정이다. 이는 합성 테스트의 원본 차량값 갱신과 다른 조작이며 실제 SDK 높이 변경 관측이 아니다. 이번 변경의 로컬 Java·Gradle·Android SDK 실행 및 새 후보 unsigned debug CI 증거는 미확보다. 새 전체 SHA의 `testPleosDebugUnitTest` XML·Gradle 로그·입력 해시·unsigned APK는 기존 GitHub Actions에서 별도 확인해야 한다. 기준 후보의 성공을 이번 변경의 성공으로 대체하지 않는다. Windows 에뮬레이터·실차·실제 런타임은 미관측이며, 합성 검증과 unsigned debug CI는 1차 필수 제안서·서명 동의서 확보, 선택 제출물, 최종 PT 작동 앱 시연을 대체하지 않는다.

일반 조건 갱신은 과거 기록을 유지한다. 새 방문지는 기록을 지우고 요청 ID가 연결된 입구 되울림은 원래 방문지와 기록을 유지한다. 근거는 `ParkingNavigationTest.simulatedAlternativeReceiptStartsEmptyAndSurvivesConditionChanges`, `echoesKeepReceiptAndNewVisitClearsIt`, `visitDuringRequestCannotReceiveOldOutcome`이다. 실패 결과는 공유 표시 모델 `RequestRecheck`에서 “입구 경로 요청 실패”, 실제 수락 상태는 “내비 요청 수락”으로 구분한다. 현재 시연 어댑터는 SIMULATED만 반환하므로 실패·실제 수락의 화면 관측을 주장하지 않는다. 거절·예외의 계약 근거는 `rejectionReplacesPreviousReceiptAndDuplicateDoesNothing`, `exceptionReplacesPreviousReceiptAndDuplicateDoesNothing`이다.


요청 **완료 후** 조건 갱신은 이미 생성된 Receipt를 유지한다(`RequestRecheckTest`). 요청 **도중** 조건 갱신은 전송 직전 보관한 방문 버전과 요청 ID로 완료를 판정한다(`RequestCompletionTest`). 같은 방문의 최신 발행 요청이면 선택 generation이 바뀌어도 선택 시설·원래 방문지·입구·반환 결과를 기록한다. 전송 전 만료·중복 소비 검사는 유지한다. 새 목적지 이벤트는 ID·이름·시설 목록이 같아도 새 방문이므로 이전 반환이 IDLE·빈 기록을 덮어쓰지 않는다. 더 최신 재진입 요청이 완료되면 이전 요청의 늦은 반환도 최신 기록을 덮어쓰지 않는다. 요청 ID가 연결된 동기·지연 입구 되울림은 방문과 기록을 유지한다.

합성 대조(2026-11-09, `RequestCompletionTest`, 실행 관측 아님): `VehicleProfile.fromHardware` → `VehicleRefreshState`로 1850mm·NON_ELECTRIC·120분·구매 할인 가정을 적용해 코엑스 0원을 선택한다. fake `requestEntrance` 내부에서 반환 전에 2250mm로 갱신한다. ACCEPTED와 SIMULATED 각각 반환 후 코엑스·원래 방문지·선택 입구·결과 Receipt가 남고 요청은 1건이다. 두 추천 기준 모두 코엑스 RESTRICTED, 대안 A 6,000원, 요청 가능 1곳이며 보존 기록의 `RequestRecheck`는 **입구 재확인 필요**다. 대안 선택까지 1건, 명시적 확인 후에만 원래 방문지와 시연 좌표 37.51400, 127.06100으로 총 2건이다. 이전 ACCEPTED 뒤 다음 요청 도중 같은 갱신 후 REJECTED 또는 RuntimeException이면 최신 실패 기록·status로 교체하고 **입구 경로 요청 실패**를 포함한다. 전송 전 갱신은 STALE·0건, 전송한 선택의 중복 확인은 추가 요청이 없다.

이는 현재 시연 어댑터의 화면 재현이나 실제 SDK 콜백 관측이 아닌 결정적 fake 계약이다. `MainActivity` 변경 없이 기존 `refresh`·`showReceipt`가 보존 Receipt를 소비한다. 차량값·출처는 상세, 대안 이유는 최대 한 줄을 유지한다. 로컬 Java 부재로 신규 테스트 실행·unsigned APK 검증은 미완료이며 새 후보 전체 SHA의 GitHub Actions XML·Gradle 로그·입력 해시·APK 검증이 필요하다. 기준 후보 성공은 새 후보 증거로 대체하지 않는다. Windows·실제 런타임 및 최종 제출 단계의 증거도 별도 미확보다.

Activity 재생성에서는 **체류 시간만 복원**한다. `onSaveInstanceState`가 체류 시간 키만 저장하고 `onCreate`가 읽어 새 `VisitQuoteState`를 만든다. 구매 할인은 저장하거나 과거 할인 키에서 읽지 않으며 미적용으로 시작한다. 차량은 `onStart`에서 다시 조회하며, 요청 기록·선택·확인창은 복원하지 않고 자동 재전송도 하지 않는다. 실제 Android 재생성은 미관측이며 소스 경로 대조만 수행했다.

## 이전 구현 기록 — 현재 절차에 적용하지 않음

아래 항목은 과거 profile-restore·route-receipt 등 후보에 적용했던 설명의 이력이다. 현재 후보의 기능이나 현존 테스트 근거로 사용하지 않는다.

- 수동 루프박스·전기차 토글, 1.85 m 고정 시작, 최초 할인 적용은 이전 시연 입력이었다. 현재 일반 화면에는 차량 수동 토글이 없다.
- 삭제된 `DemoProfile`·`DemoProfileTest`의 네 값·네 키 복원 및 16조합 왕복 검사는 과거 profile-restore 기록이다. 현재 복원 근거는 위 MainActivity 체류 시간 단일 키 경로뿐이다.
- “마지막 요청 없음”, “마지막 요청 · 과거 확인 기록 (현재 후보 판단과 별개)”, “시연 어댑터 요청 처리 · 실제 길안내 시작 아님”을 화면에 기대했던 기록은 이전 UI 설명이다. 현재 요약·상세·결과 문구는 위 절을 따른다.
- 당시 selection-ready(`b8af43df19b58ff22e41c92f1a610eb3cdfe9dab` 기준), profile-restore(`589948ea1ffe72992434f36c9f264cfa5834d703` 기준), route-receipt(`d7e43092e48d140d5fb363f2d69ae4ef73291c53` 기준)의 로컬 Gradle 시도는 `gradle: command not found`(127)로 미실행이며 Java 도구도 없었다는 기록이다. 당시 지정 다섯 파일 diff 확인은 이번 두 문서 변경의 검증과 별개다. 과거 후보의 CI·XML·APK를 이번 결과로 사용하지 않는다.

## 추천 판단 설명 (decision-basis · 소스 대조일 2026-11-09)

이 절은 현재 구현과 합성 검증 입력을 설명한다. 실제 AAOS/Pleos 속성 수신이나 Windows 화면 관측 기록이 아니다. `RecommendationExplanation.create`는 카드 계산에 사용한 동일 `VehicleProfile`·추천 기준과 이미 정렬된 `Option` 목록을 읽는다. 적합성·요금·정렬·요청 가능 여부를 다시 계산하지 않는다. 설명 생성과 갱신에는 내비 요청 부작용이 없으며 선택 확인만 요청을 발생시킨다.

첫 화면에서는 요청 가능한 대안이 추천될 때만 부설 후보가 제외된 이유 한 줄을 추가한다. 정상 부설 추천에는 추가 설명을 넣지 않는다. 추천 카드 또는 후보 행의 **상세**에서 차량 높이(mm)·연료 유형·높이 출처와 해당 시설의 제한 높이(m·mm), 판정 사유를 확인한다. 높이가 하드웨어에서 왔으면 **차량 속성값**, 정확한 모델·연식의 보완값이면 **제조사 기본 제원**과 제원 URL을 표시한다. 조회 중에는 카드를 비우고 열린 상세도 닫아 이전 높이·추천 이유를 지운다. 목적지 변경도 목록과 설명을 새 결과로 교체하고 열린 상세를 닫는다. 빈 목록은 **시설 정보 없음**으로 구별한다.

아래는 `RecommendationExplanationTest`의 합성 입력 → 기존 판정 → 설명 assertion과 대조한 향후 리허설 기대값이다. 원본 차량 속성 입력이나 실제 차량 관측을 뜻하지 않는다. 총높이 보완은 별도 사용자 확인값이다. 모든 요금은 120분·할인 미적용이다.

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

이번 로컬 환경에는 PATH의 Java·javac·Gradle이 없어 JVM 테스트와 unsigned APK 빌드를 실행하지 못했다. 도구 설치·커밋·푸시·CI 호출은 하지 않았다. `git diff --check`와 지정 editPaths 제한 검사를 수행하며, 이 검사는 테스트 실행 성공을 뜻하지 않는다. 위 신규 테스트와 기존 `ParkingNavigationTest` 등의 XML은 아직 확보하지 못했다.

후속 구현에서 Oracle이 만든 **새 후보 전체 SHA**에 대한 기존 GitHub Actions 결과를 확인해야 한다. `pleos-build-<full SHA>` 이름과 `source-sha.txt`를 대조하고, 동일 SHA 체크아웃에서 README의 `sha256sum -c /path/to/input-sha256.txt`, 아티팩트 폴더에서 `sha256sum -c apk-sha256.txt`를 확인한다. `gradle.log`의 `testPleosDebugUnitTest`·unsigned `assemblePleosDebug` 성공, 기존·신규 테스트 XML의 실패·오류·건너뜀 모두 0, 워크플로 APK 패키지·ZIP·unsigned 검사 성공을 확인할 때까지 실행 수용 항목 8은 대기다. 기준 SHA나 과거 성공 보고로 대체하지 않는다.

Windows 설치·화면·재생성, 실제 SDK 콜백·차량 신호, 실차·음성·음향·공개 배포 증거는 없다. 별도 승인된 환경에서 위 기대값과 실제 화면·신호를 대조해야 한다.


## 차량 서비스 연결 지연 재조회 (vehicle-service-reconnect · 2026-11-08)

이번 소스는 최초 읽기의 서비스 미연결과 연결 후 개별 속성 누락을 구별한다. `Car` 생성 결과가 없거나 연결되지 않았거나 속성 서비스 객체가 없으면 `DISCONNECTED`로 분류한다. 이 첫 결과에만 500ms 뒤 백그라운드 읽기를 한 번 더 예약한다. 두 번째 결과가 전체 3000ms 마감 전에 반환되면 그 결과로 종료하고, 반환되지 않으면 무응답 만료로 UI 대기를 종료한다. 연결된 서비스에서 개별 속성이 미지원·접근 불가·사용 불가이면 해당 값만 누락되며 재시도하지 않는다. Automotive 기능 부재와 바깥 API/권한 예외는 `UNAVAILABLE`로 종료한다. 이 분류와 지연값은 앱 내부 정책이며 실제 Pleos 연결 시간의 관측값이 아니다.

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


## 높이 미확인 복구 합성 예시 (소스/assertion 대조일 2026-11-09)

주 리허설의 높이 미확인 분기는 아래 기존 테스트와 대조한다. 조건은 **원본 높이·연료 모두 미확인, 120분·구매 할인 미적용·사용자 제외 없음**으로 고정한다. **1850mm는 합성 검증용 사용자 확인값**이다. 실제 총높이를 모르는 사용자가 이 숫자를 그대로 입력하는 절차가 아니다. 실제 차량값 수신은 AAOS 원본과 그 출처로, 사용자 보완은 직접 확인값이라는 별도 근거로 취급한다. 상세에서 원본 차량값·기존 출처, 사용자 직접 확인 총높이·출처, 판단 적용 높이를 각각 읽는다. 입력 정책과 조작은 아래 **총높이 보완 — 현행 절차**를 따른다.

| 단계·고정 조건에서의 기대값 | 기존 assertion 대응 |
| --- | --- |
| 보완 전: 요청 가능 후보 0곳, 요청 보류 | `VehicleHeightTest.missingHeightRecoveryDeletionAndBoundaryPreserveUnknownFuel`: 최초 `requestableCount` 0 |
| 사용자 확인 총높이 1850mm 적용 후: 요청 가능 후보 1곳, 대안 A만 가능. 코엑스는 연료 확인 필요·NEEDS_CHECK이며 비전기차로 간주하지 않음 | 같은 테스트: 보완 후 count 1·첫 후보 비부설·코엑스 Fit와 연료 사유·상세의 연료 확인 필요 및 비전기차 문구 부재 |
| 같은 보완 후 대안 A의 120분 예상 요금 6,000원 | `VehicleReadResultTest.expiredUnknownCanSupplementHeightAndOnlyConfirmRequestsEntrance`: 만료 후 연료 NEEDS_CHECK 유지·count 1·대안 A 이름·`expectedWon` 6000 |
| 보완값 삭제 후: 적용 높이 null, 요청 가능 후보 0곳으로 복귀·요청 보류 | `VehicleHeightTest.missingHeightRecoveryDeletionAndBoundaryPreserveUnknownFuel`: clear 후 effectiveMm null·count 0. 위 세 단계의 후보 수는 0→1→0곳이며 요청 횟수와 다름 |
| 보완·계산·선택까지 요청 0건; 유효한 명시적 확인만 선택 입구 37.51400, 127.06100으로 정확히 1건 | 위 `VehicleReadResultTest`: select 후 fake.requests 비어 있음, confirm 후 size 1·선택 좌표·원래 방문지 유지, 중복 확인 STALE |

취소는 `MainActivity.showTotalHeight`의 취소 리스너가 값을 적용하지 않으며, 잘못된 입력은 오류를 표시하고 입력창과 기존 보완값을 유지한다. `VehicleHeightTest.inputPolicyInvalidAndCancelledInputDoNotMutateAndSessionsExpire`가 입력 불변성을 대조한다. 적용·삭제의 `refresh`는 후보 재판정과 기존 선택 만료를 함께 수행하므로 이전 확인창을 재사용하지 않는다. 원본 높이가 있으면 삭제 후 그 값으로 판단하고, 이 합성 예시처럼 원본도 없으면 미확인으로 돌아간다.

위 fake 테스트의 거절은 REJECTED·실패이며 성공이 아니다. `ParkingNavigationTest.rejectionSimulationAndExceptionAreNotSuccess`의 거절·예외·시연 구분과 대조한다. 현재 화면의 `SampleTrip.SimulatedNavigation`은 SIMULATED를 반환하므로 **“시연 요청 완료”**이며 실제 내비 수락이나 길안내 시작 증거가 아니다. 이 표는 소스·assertion 대조이며 실행 통과·Windows 화면 관측 기록이 아니다.

## 총높이 보완 — 현행 절차 (소스 대조일 2026-11-09)

원본 높이·연료는 AAOS 조회 대상이며 수정하지 않는다. 시설 **상세 → 총높이 보완**에서 **루프박스·적재물 포함 직접 확인한 총높이(mm)**를 입력한다. 정수 1000~4000mm는 앱 내부 입력 정책이며 차량 제원이나 입차 안전 기준이 아니다. 적용은 원본 높이와 사용자 확인값 중 큰 값을 판단에 사용한다. 원본이 없으면 확인값을 사용하고 둘 다 없으면 미확인이다. 취소·잘못된 입력은 기존 보완값을 유지하며 **보완값 삭제**는 원본 높이 판단으로 돌아간다. 자동 장착물 감지는 구현하지 않았다.

아래 수치는 `VehicleHeightTest`의 합성 AAOS 입력과 사용자 확인값이다. 실제 Pleos 수신·Windows 화면 관측이나 입차 안전 보증을 뜻하지 않는다. 요금 조건은 120분·구매 할인 미적용이다.

| 조작 / 합성 조건 | 기대 결과 및 assertion 근거 |
| --- | --- |
| 하드웨어 1850mm·NON_ELECTRIC → 상세에서 확인 총높이 2250mm 적용 | 코엑스 12,000원 우선 → 대안 A 6,000원 우선. 코엑스 RESTRICTED·요청 불가. 원본 1850mm 보존 (`hardwareSupplementFlowsThroughExplanationAndConfirmedNavigation`) |
| 높이·연료 누락 → 확인 총높이 1850mm 적용 → 보완값 삭제 | 요청 가능 0→1→0건. 보완 후 대안 A만 가능, 코엑스는 연료 확인 필요. 비전기차로 표시하지 않음 (`missingHeightRecoveryDeletionAndBoundaryPreserveUnknownFuel`) |
| 총높이 2200mm·NON_ELECTRIC | 코엑스 경계값 NEEDS_CHECK, 요청 불가 (같은 테스트) |
| 정확한 HYUNDAI·IONIQ 5 N·2025 제원 1585mm → 확인 총높이 2250mm | 판단 2250mm, 원본 제조사 출처·URL과 사용자 직접 확인값을 별도 표시. 하드웨어 2400mm이면 사용자 2250mm보다 큰 2400mm 적용 (`catalogAndHigherHardwareKeepTheirOriginalSources`) |
| 1000·4000 입력 / 빈값·0·음수·999·4001·소수·문자·정수 초과 입력 / 취소 | 경계 허용 / 미적용 / 기존값 유지 (`inputPolicyInvalidAndCancelledInputDoNotMutateAndSessionsExpire`, 적용·취소 리스너) |
| 보완 후 같은 Activity의 stop → onStart | 확인 총높이 유지 후 차량 재조회. 이전 입력창 토큰의 적용·삭제 차단. 현재 토큰으로 명시적 삭제 가능. 새 Activity는 새 객체이므로 미입력이며 Bundle에는 체류 시간만 저장 (VehicleHeightTest·VehicleRefreshStateTest 및 MainActivity 수명주기 소스 대조) |
| 적용·설명 갱신 → 이전 선택 확인 → 새 대안 A 선택 확인 | 갱신 자체 요청 0건, 이전 선택 STALE. 새 선택 확인만 원래 방문지와 37.51400, 127.06100으로 1건. 중복 확인 추가 요청 없음. fake REJECTED는 실패로 기록 (`hardwareSupplementFlowsThroughExplanationAndConfirmedNavigation`) |

원본 높이·기존 출처, 사용자 확인 총높이, 판단 적용 높이는 상세에만 구분한다. 첫 화면은 기존 대안 이유 최대 한 줄을 유지한다. 삭제·적용은 refresh에서 후보·설명을 함께 갱신하고 선택을 만료시킨다. 새 테스트는 기존과 같은 `app/src/test/java`에 있어 `testPleosDebugUnitTest` 대상이다. 위 assertion 및 소스 경로 대조는 실행 통과 증거가 아니다.

이번 작업 환경 PATH에 Java·javac·Gradle이 없어 테스트·unsigned APK 빌드는 실행하지 못했다. 도구 설치·네트워크·CI 호출·커밋·푸시는 수행하지 않았다. 새 후보 SHA와 CI 아티팩트가 제공되지 않아 수용 항목 8은 대기다. Oracle의 새 커밋 후 GitHub Actions에서 `testPleosDebugUnitTest`·`assemblePleosDebug`, 신규 VehicleHeightTest 및 기존 ParkingNavigationTest 등 XML 실패·오류·건너뜀 0을 확인해야 한다. `source-sha.txt`와 `pleos-build-<full SHA>`가 새 전체 SHA와 일치해야 하며 README의 두 sha256sum 검사, 입력·APK 해시, Gradle 로그, APK 패키지·ZIP·unsigned 검사를 확인한다. 기준 후보 성공으로 대체하지 않는다. 커밋 후 `git diff --check 1cbfb84534174c5d459131be2c52581939d66cc7 HEAD` 및 같은 범위의 지정 6개 경로 제한도 확인해야 한다.

1차 제안서·서명 동의서 필수와 최종 PT 작동 앱 시연 필수 구분을 유지한다. 외부 자료는 기존 출처·확인일을 따르며 이번에는 재확인하지 않았다. Windows 설치·화면·수명주기, 실제 SDK 콜백·차량 신호·실차·음성·음향 증거는 미확보다.


### 방문별 요금 조건: fake 합성 검증

새 목적지를 받으면 체류 시간은 유지하고 구매 할인 가정은 미적용으로 초기화한다. 사용자는 새 방문의 할인 자격을 다시 확인해야 한다. `ParkingNavigation.visitVersion`은 상관 요청 ID로 식별된 입구 되울림을 제외한 목적지 이벤트마다 증가한다. ID·이름이 같은 이벤트도 새 방문이다. 선택 만료 generation과 달리 일반 refresh·선택 만료·확인은 방문 버전을 바꾸지 않아 같은 방문의 할인은 유지된다.

`VisitQuoteStateTest`의 재현 순서와 기대값(실제 SDK 관측 아님): 합성 AAOS 1850mm·NON_ELECTRIC·120분으로 fake 방문 A 수신 → 요금창 할인 적용 → 코엑스 0원 → 같은 SampleTrip 시설의 방문 B 수신 → 할인 false·120분 유지·코엑스 12,000원·대안 A 6,000원. A의 요금창 토큰으로 60분·할인을 적용해도 B의 두 조건은 바뀌지 않는다. B에서 새로 연 창은 현재 할인 상태를 표시하고 적용 가능하며 취소는 값을 쓰지 않는다. 창 제목은 방문지이며 체류 시간은 다음 방문에도 유지하고 구매 할인은 현재 방문에만 적용한다고 설명한다. 이전 창의 적용은 토큰 검사로 거부하고 오류를 표시하며 새 방문 조건을 덮어쓰지 않는다.

할인 초기화·요금 갱신·오래된 창 적용은 요청 0건이고 A의 선택은 STALE이다. B의 코엑스 선택 확인만 B / 37.51200, 127.06000으로 1건 요청한다. 중복 확인은 추가 요청하지 않고 REJECTED는 실패로 기록한다. 동기·지연 입구 되울림은 방문과 할인을 유지한다. 할인과 무관하게 2250mm의 코엑스는 RESTRICTED이며 높이 누락은 요청 가능 0건이다. 저장된 60분으로 새 상태를 만들면 60분·할인 미적용이고 같은 Activity의 일반 갱신은 할인을 유지한다.

이번 변경의 근거는 상태·리스너·Bundle 경로와 합성 assertion의 소스 대조다(2026-11-09). 새 후보의 `testPleosDebugUnitTest` XML, `assemblePleosDebug` 로그 및 전체 SHA에 결합된 source-sha.txt·입력 해시·unsigned APK 해시·패키지/ZIP 검사는 기존 GitHub Actions에서 별도 확인해야 한다. 기준 후보 성공으로 대신하지 않는다. Windows 에뮬레이터·실차·실제 SDK 콜백·Activity 재생성 화면 관측은 없다. 1차 필수 제안서·서명 동의서 확보와 최종 PT 작동 시연 완료를 뜻하지 않는다.


### 추천 기준 선택 — fare-priority (소스 대조일 2026-11-09)

시설 **상세 → 추천 기준 → 부설 우선 / 요금 우선**에서 하나를 선택하면 즉시 후보와 이유를 갱신하고 이전 선택은 만료한다. 기본값은 부설 우선이다. 같은 Activity의 일반 갱신에는 선택한 기준을 유지한다. Bundle에는 체류 시간만 저장하므로 새 Activity는 부설 우선으로 시작한다. 현재 기준·차량값·출처는 상세에 두고 첫 화면 대안 이유는 최대 한 줄이다. 확인 전에는 경로를 요청하지 않는다.

아래는 `ParkingPriorityTest`와 `SampleTrip.LOTS`를 대조한 **합성 검증 기대값**이며, 테스트 실행 통과·실제 절감·SDK 관측 기록이 아니다.

| 합성 AAOS 입력과 상세 조작 | 기대 추천 |
| --- | --- |
| 1850mm·NON_ELECTRIC·120분·할인 미적용, 기본 부설 우선 | 코엑스 12,000원, 대안 A 6,000원 순 |
| 같은 조건에서 요금 우선 선택 | 대안 A 6,000원, 코엑스 12,000원 순. 이유는 ‘요금 우선 · 예상 요금이 낮은 대안’이며 코엑스를 제한 시설로 표현하지 않음 |
| 부설 우선으로 복귀 | 코엑스 12,000원이 다시 첫 후보 |
| 요금 우선·120분·할인 적용·1850mm·NON_ELECTRIC | 코엑스 0원이 첫 후보 |
| 위 조건에서 높이만 2250mm로 변경 | 대안 A 6,000원 추천. 코엑스 RESTRICTED·요청 불가, 이유는 높이 초과 |
| 1850mm·ELECTRIC / 높이 누락 | 코엑스 NEEDS_CHECK / 전체 요청 가능 0건 |

요금 미확인은 알려진 요금 뒤이며 0원은 유효한 요금이다. 무료여도 입구 누락·제한·확인 필요이면 요청 가능한 유료 후보보다 뒤이며 선택할 수 없다. 기준 변경·계산·설명만으로 fake 요청은 0건이다. 변경 전 선택 확인은 STALE이며 새 대안 A 선택 확인만 원래 방문지와 시연 좌표 37.51400, 127.06100으로 1건 요청한다. 중복 확인은 추가 요청하지 않고 REJECTED는 실패로 기록한다. 설명은 전달된 후보의 판정과 순서를 사용하며 재계산하지 않는다.

로컬 PATH에 Java·javac·Gradle이 없어 JVM 테스트·unsigned APK 빌드는 미실행이다. 도구 설치·네트워크·CI 호출·커밋·푸시는 하지 않았다. 새 후보 커밋은 Oracle의 후속 단계이며, 기존 GitHub Actions에서 새 전체 SHA에 결합된 `testPleosDebugUnitTest`·`assemblePleosDebug` 성공과 기존·신규 XML 실패·오류·건너뜀 0을 확인해야 한다. `source-sha.txt`와 `pleos-build-<full SHA>`의 일치, README의 두 sha256sum 검사, 입력 해시·APK 해시·Gradle 로그·APK 패키지·ZIP·unsigned 검사와 기준 694cf8d2a191945d64738276fea096a7e0e270d6 대비 전체 diff·지정 7개 경로 제한은 새 후보에서 검증 대기다. 기준 후보 성공을 새 증거로 대신하지 않는다.

Windows 화면·Activity 재생성·실제 SDK 콜백·실차·음성·음향 관측 증거는 없다. 외부 사실은 기존 출처·확인일을 유지하며 외부 재확인은 하지 않았다. 1차 필수 제안서·서명 동의서, 선택 프로토타입·적격 GitHub/APK, 최종 PT 작동 시연의 제출 단계 구분을 유지하며 이 합성 사례는 제출·시연 완료를 뜻하지 않는다.

로컬 정적 확인: 기준 SHA 대비 작업 트리 `git diff --check`와 미추적 신규 테스트를 포함한 정확한 7개 editPaths 제한 검사가 통과했다. MainActivity의 기본 필드·상세 전용 리스너·동일 기준/후보 전달·Bundle 미저장·`setSingleLine(true)`도 소스로 대조했다. 이 결과는 JVM assertion 실행이나 새 커밋 CI 성공을 뜻하지 않는다.


### 입구 좌표 수치 검사 — coord-guard (소스 대조일 2026-11-09)

`Entrance.hasValidCoordinates()`는 위도·경도 모두 `Double.isFinite`이며 위도 -90~90, 경도 -180~180의 포함 범위일 때만 참이다. 0과 경계값은 통과하며 보정하거나 서로 바꾸지 않는다. 이는 수치 유효성 검사이며 실제 입구 위치 확인이나 입차 안전 보증이 아니다. `Option.requestAvailability()`를 통해 정렬·요청 가능 후보 수·`routeRequest`·`ParkingNavigation.select`가 같은 판정을 사용한다. 좌표 오류는 Fit·요금을 바꾸지 않고 “입구 좌표 확인 필요”로 요청을 차단한다.

아래는 `EntranceCoordinateTest`의 합성 AAOS 입력과 `SampleTrip` 시설 복사본에 대한 assertion 기대값이다. 실제 차량 수신값·SDK 관측이나 테스트 실행 통과 기록이 아니다. 시설은 좌표만 바꾸며 외부 시설 사실을 갱신하지 않는다.

| 합성 조건 / 리허설 조작 | 기대 표시·요청 결과 |
| --- | --- |
| 1850mm·NON_ELECTRIC·120분·구매 할인 적용, 코엑스 위도만 NaN | 부설 우선·요금 우선 모두 대안 A 6,000원 첫 후보, 요청 가능 1곳. 코엑스 Fit은 MATCHES_RECORDED_RULES, 기본 12,000원·예상 0원 유지, routeRequest·select는 비어 있음 |
| 위 조건의 추천 카드와 코엑스 상세 확인 | 첫 화면 이유 “코엑스 지하주차장 · 입구 좌표 확인 필요” 최대 한 줄. 입차 금지로 표현하지 않음. 기존 MainActivity의 `setSingleLine(true)` 경로를 사용하며 상세에는 차량값·출처와 requestAvailability 사유 유지 |
| 높이 1850→2250mm 변경, 코엑스 좌표 정상·대안 A 경도 NaN | 요청 가능 1→0곳. 코엑스 RESTRICTED·“표기 높이 제한 초과”, 대안 A MATCHES_RECORDED_RULES·“입구 좌표 확인 필요”. “요청 가능한 입구 없음 · 요청 가능한 후보 0곳”; 모든 선택·경로 생성 차단 |
| 높이 누락 / 1850mm·ELECTRIC / 정상 대안 A의 입구 누락 | 높이 누락 NEEDS_CHECK, 코엑스 전기차 조건 미확인 NEEDS_CHECK, 입구 누락은 Fit 유지·“입구 정보 미확인”. 각 합성 목록 요청 가능 0곳·선택 차단 |
| 계산·설명·잘못된 좌표 선택 → 유효한 대안 A 새 선택 → 확인 | 확인 전 fake 요청 0건. 확인 후 원래 방문지 “스타필드 코엑스몰”과 37.51400, 127.06100으로 정확히 1건. 입구 되울림은 방문지 유지. REJECTED는 실패 receipt, 중복 확인 STALE·추가 요청 없음 |
| 위도·경도 각각 NaN·±무한대·포함 범위 바로 밖 / 위도 ±90·경도 ±180·0 조합 | 오류 입력마다 요청 불가·선택 차단. 유효 경계 조합은 전달 좌표와 객체 그대로 유지 |

테스트는 기존 `app/src/test/java`에 있어 `testPleosDebugUnitTest` 대상이다. 이번 환경 PATH에서 Java·javac·Gradle을 찾지 못해 JVM 테스트·unsigned APK 빌드는 실행하지 못했다. 도구 설치·네트워크·자격 증명·CI 호출·커밋·푸시는 하지 않았다. 로컬 `git diff --check`와 네 editPaths 제한을 확인하며 이는 JVM assertion 실행 증거가 아니다.

Oracle의 새 후보 커밋 후 기존 GitHub Actions에서 `testPleosDebugUnitTest`·`assemblePleosDebug` 성공, 기존·신규 XML 실패·오류·건너뜀 모두 0을 확인해야 한다. 새 전체 SHA가 `source-sha.txt`와 `pleos-build-<full SHA>`에 일치하고 README의 두 `sha256sum` 검사, 입력 해시·APK 해시·Gradle 로그·APK 패키지·ZIP·unsigned 검사가 통과해야 한다. 기준 SHA 성공으로 대신하지 않는다. 새 커밋이 없으므로 해당 아티팩트는 미확보이며 수용 항목 7은 대기다. 커밋 후 `git diff --check 492be84715c2122cc00d04f42b4d4a9f3f3551d4 HEAD`와 같은 범위 네 파일 제한·전체 diff 검증도 대기다. 작업 트리 검사는 커밋 범위 검사를 대신하지 않는다.

외부 출처·확인일은 기존 SampleTrip 및 README 기록을 유지하며 이번에는 외부 재확인을 하지 않았다. Windows 설치·화면·실제 SDK 콜백·실차·음성·음향·공개 배포 증거는 없다. 1차 필수 제안서·서명 동의서, 선택 프로토타입·적격 GitHub/APK, 최종 PT 필수 작동 앱 시연의 구분을 유지한다. 합성 검증·unsigned CI·실제 런타임 관측·제출 완료는 별도 단계다.


### 등록된 허용 차폭 비교 — width-fit (소스 대조일 2026-11-09)

아래는 새 `VehicleWidthTest`의 합성 AAOS 입력과 가상 시설 사례이며 외부 시설 사실이나 실제 신호 관측이 아니다. 높이 1850mm·NON_ELECTRIC·120분·할인 미적용·정상 입구를 고정한다. 가상 부설은 최대 허용 차량 너비 2000mm·0원, 가상 대안은 2200mm·6,000원이다. 너비 **1990→2010mm**만 바꾸면 부설 우선·요금 우선 모두 **0원 부설 → 6,000원 대안**으로 바뀐다. 부설 요금은 0원 그대로이고 RESTRICTED·요청 불가가 된다. 이는 차량 제원과 같은 기준의 **최대 허용 차량 너비** 비교이며 통로 실측 폭 비교나 통과 안전 보증이 아니다. 현재 `SampleTrip.LOTS`에는 차폭 조건이 등록되지 않았다. 코엑스 차폭 제한을 추가하거나 추정하지 않았다.

2000mm 경계는 충족한다. 차량 너비 null·배열 누락·0·음수는 미확인으로 처리하여 조건이 등록된 두 시설 모두 NEEDS_CHECK·요청 불가다. 등록 조건의 허용값 누락·0 이하도 NEEDS_CHECK이며, 조건 자체 미등록인 기존 시설은 기존 판단을 유지한다. 미등록을 차폭 무제한·적합으로 표시하지 않는다. 상세에 차량 너비와 출처, 시설 허용값과 출처, 판단 범위를 표시하고 첫 화면에는 짧은 대안 이유 하나만 최대 한 줄로 표시한다.

정확한 HYUNDAI·IONIQ 5 N·2025에서 높이 1850mm만 하드웨어 입력이면 높이는 차량 속성값, 너비 1940mm는 제조사 보완으로 각각 표시한다. 하드웨어 너비 2010mm가 있으면 이를 우선하며 너비 제조사 출처 표시는 사라진다. 제조사 보완 범위와 기존 외부 출처·확인일은 VehicleProfile 및 README 기록을 유지하며 이번에는 외부 재확인하지 않았다.

소스 assertion 근거는 `widthChangeFlowsFromHardwareThroughBothPrioritiesAndConfirmedNavigation`(두 기준·순서·0원 보존·RESTRICTED·설명·선택·요청), `inclusiveBoundaryAndUnknownWidthsRemainDistinctFromUnregistered`(경계·누락·미등록·요청 수), `independentCatalogSourcesAndExactModelRule`(독립 출처·정확한 모델), `widthMatchDoesNotBypassExistingBlocksAndExplanationUsesSuppliedVerdict`(높이·전기차 정책·좌표 차단·전달 판정)이다. 변경·재계산·설명·선택까지 fake 요청 0건이고 이전 선택은 STALE이다. 새 대안을 확인할 때만 원래 방문지와 가상 입구 37.6, 127.1로 1건 요청한다. 되울림은 방문지를 보존하며 REJECTED는 실패 receipt로 남고 중복 확인은 추가 요청하지 않는다. 테스트는 기존 `app/src/test/java`에 있어 `testPleosDebugUnitTest` 대상이다.

로컬 PATH에 Java·javac·Gradle이 없어 JVM 테스트·unsigned APK 빌드는 미실행이다. 도구 설치·네트워크·자격 증명·CI 호출·커밋·푸시는 수행하지 않았다. 합성 assertion 소스 대조는 실행 통과 증거가 아니다. Oracle의 새 커밋 후 기존 GitHub Actions에서 새 전체 SHA에 결합된 `testPleosDebugUnitTest`·`assemblePleosDebug` 성공, 기존·신규 XML 실패·오류·건너뜀 모두 0을 확인해야 한다. `source-sha.txt`와 `pleos-build-<full SHA>` 일치, README의 두 `sha256sum` 검사, 입력 해시·APK 해시·Gradle 로그·APK 패키지·ZIP·unsigned 검사는 대기다. 기준 후보 성공으로 대체하지 않는다.

작업 트리의 기준 `1a984a3c81387935ad2ad7bed53c10f156119bd9` 대비 diff 공백 검사와 지정 8개 editPaths 제한을 확인한다. 새 커밋이 없으므로 같은 기준부터 새 HEAD까지의 최종 diff 검사 및 후보 아티팩트는 대기다. README 증거 계약·주행 제한·서명·권한·의존성·워크플로·게시 설정은 변경하지 않는다.

Windows 설치·화면·실제 SDK 콜백·실제 차량 신호·실차·음성·음향·공개 배포 관측 증거는 없다. 합성 테스트·unsigned CI·실제 런타임 관측·제출 완료는 별도 단계다. 1차 필수 제안서·서명 동의서 확보, 선택 프로토타입·적격 GitHub/APK, 최종 PT 필수 작동 앱 시연의 구분을 유지하며 이번 작업은 제출·시연 완료를 뜻하지 않는다.


### 요금 오류 격리 — tariff-fallback (소스 대조일 2026-11-09)

아래는 `TariffFallbackTest`의 **합성 AAOS 입력·시설 복사본에 대한 assertion 기대값**이다. 실제 시설 요금 오류나 SDK 수신 관측, 테스트 실행 통과 기록이 아니다. 현재 화면에는 오류 요금을 주입하는 조작이 없다. 시설의 과금 단위만 테스트에서 바꾸며 기존 외부 출처·확인일은 SampleTrip 및 README 기록을 유지한다. 이번에는 외부 재확인을 하지 않았다.

| 합성 입력·변경 | 기대 사용자 결과 |
| --- | --- |
| 1850mm·NON_ELECTRIC·120분·할인 미적용, 코엑스 복사본의 과금 단위만 0 | 전체 3개 후보 계산 완료. 코엑스 기본·예상 요금 미확인, MATCHES_RECORDED_RULES·요청 가능. 요청 가능 2곳. 부설 우선은 코엑스, 대안 A 순; 요금 우선은 대안 A 6,000원, 코엑스 순. 후자의 이유는 ‘요금 우선 · 요금 확인된 대안’ |
| 대안 A 복사본의 과금 단위만 0, 같은 조건에서 높이 1850→2250mm | 두 기준 모두 코엑스 12,000원에서 요금 미확인 대안 A로 추천 변경. 코엑스 RESTRICTED·요청 불가, 이유는 ‘코엑스 지하주차장 · 높이 초과’. 요청 가능 1곳 |
| 모든 시설 요금 오류 + 합성 높이·전기차·차폭 제한·입구 누락·좌표 오류 | 요금 미확인과 별개로 기존 Fit·요청 차단 유지. 신규 합성 5개 목록은 요청 가능 0곳 |
| 재계산·설명·선택 후 2250mm 사례의 대안 A 확인 | 확인 전 fake 요청 0건. 확인 후 원래 방문지 ‘스타필드 코엑스몰’과 37.51400, 127.06100으로 정확히 1건. 입구 되울림은 방문지 유지. REJECTED는 실패 receipt, 중복 확인은 STALE·추가 요청 없음 |

과금 단위 0·음수, 단가·무료 시간·체류 시간 음수는 할인 선택과 무관하게 null이며 기본·예상 요금 모두 미확인이다. 요금 미확인은 무료나 입차 금지를 뜻하지 않는다. 정상 15분당 1,500원·무료 120분은 60분 미할인 6,000원, 120분 할인 0원, 121분 할인 1,500원이다. 체류 0분·단가 0원은 유효하다. 단위 Integer.MAX_VALUE·체류 120분·단가 1,500원은 1,500원이며, 단위 1분·체류 2분·단가 Integer.MAX_VALUE는 미확인이다. 중간 계산은 long이고 최종 Integer.MAX_VALUE까지 유효하다. 알려진 요금의 대안을 미확인 금액보다 저렴하다고 표현하지 않는다. 설명은 전달된 Option을 사용하며 차량 제한이면 기존 제한 이유를 사용한다.

기존 MainActivity의 `won(null)`은 ‘요금 미확인’을 표시한다. 첫 화면에 새 설정·차량값·출처를 추가하지 않으며 대안 이유는 기존 최대 한 줄을 유지한다. `ParkingPlanTest`의 기존 금액 기대값은 유지하고 반환형만 맞췄다.

로컬 PATH에 Java·javac·Gradle이 없어 `testPleosDebugUnitTest`·`assemblePleosDebug`는 미실행이다. 합성 assertion 소스 대조는 실행 증거가 아니다. 도구 설치·네트워크·자격 증명·CI 호출·커밋·푸시는 하지 않는다. Oracle의 새 커밋 후 기존 GitHub Actions에서 새 전체 SHA에 결합된 테스트 XML(기존·신규 실패·오류·건너뜀 모두 0)과 Gradle 로그를 확인해야 한다. `source-sha.txt`·`pleos-build-<full SHA>` 일치, README의 두 `sha256sum` 검사, 입력 해시·unsigned APK·패키지·ZIP·unsigned 검사는 대기다. 기준 후보 성공으로 대신하지 않는다.

기준 `56d0700dad90011dda99dcb69ef0488f3dc1306f` 대비 작업 트리의 공백·전체 diff·신규 테스트 포함 지정 6개 editPaths 제한을 로컬 확인한다. 새 커밋이 없으므로 같은 기준의 최종 HEAD 범위 검사와 새 SHA 아티팩트 증명은 대기다. README 증거 계약·주행 제한·서명·권한·의존성·워크플로·게시 설정은 변경하지 않는다.

Windows 설치·화면·실제 SDK 콜백·실제 차량 신호·실차·음성·음향·공개 배포 관측 증거는 없다. 합성 검증·unsigned CI·실제 런타임 관측·제출 완료는 별도 단계다. 1차 필수 제안서·서명 동의서 확보, 선택 프로토타입·적격 GitHub/APK, 최종 PT 필수 작동 앱 시연의 구분을 유지하며 이번 작업은 제출·시연 완료를 뜻하지 않는다.


### 차량 조회 대기 상한 (2026-11-09 소스·합성 검증 설계)

최초 조회부터 최대 한 번의 재시도까지 **3000ms 앱 내부 대기 정책**을 공유한다. `Session.start`에 전달한 단조 시각으로 마감을 한 번 정하고 `Handler`와 동일한 `SystemClock.uptimeMillis()`로 예약·결과 수용·재시도 시작을 검사한다. 첫 미연결 결과에만 500ms 뒤 재시도를 예약하며 마감은 연장하지 않는다. 2800ms에 첫 미연결 결과가 오면 3000ms 만료가 먼저여서 추가 읽기는 없고, 100ms에 오면 600ms에 두 번째 읽기를 시작해도 마감은 3000ms다. 두 번째 결과가 마감 전에 반환되면 그 결과로 종료한다. 어느 읽기든 반환되지 않으면 마감에 UI 대기를 종료한다. 마감 이상 시각의 결과는 만료로 처리하며, 완료·만료·화면 종료 뒤 오래된 반환값과 타이머는 무시한다.

무응답 만료 후 높이·너비는 null, 연료는 `NEEDS_CHECK`이고 요청 가능 후보는 0곳이다. `readingVehicle`을 해제하고 `refresh`하여 후보 상세와 기존 **총높이 보완**을 열 수 있다. 첫 화면에 설정·차량값·출처를 추가하지 않는다. 상세에서 사용자 직접 확인 총높이 1850mm를 적용하면 연료 미확인은 유지되고, 120분·할인 미적용에서 대안 A만 6,000원으로 요청 가능하다. 만료·입력·계산·선택은 요청을 발생시키지 않는다. 확인 후 원래 방문지와 시연 입구 37.51400, 127.06100으로 한 번 요청하며, 거절은 실패이고 중복 확인은 추가 요청하지 않는다. 연료 미확인과 시설의 명시적 입차 제한은 구별한다.

`VehicleReadResultTest`의 가짜 시계·읽기·스케줄러는 sleep이나 Android 런타임 없이 공유 Session을 실행한다. `noResponseExpiresOnceAtFixedDeadlineAndLateResultsAreIgnored`는 2999/3000ms·중복 만료·늦은 결과, `retrySharesDeadlineWhetherFirstResponseIsEarlyOrLate`는 1/2회 읽기와 고정 마감, `deadlineResultsExpireAndOldEventsCannotAffectReplacement` 및 기존 세션 테스트는 이벤트 순서·세션 교체를 검사한다. `hardwareBeforeDeadlineKeepsRecommendationsAfterExpiryEvent`는 합성 AAOS 1850mm·비전기차의 코엑스 12,000원 우선/요청 가능 2곳과 2250mm의 대안 A 6,000원 우선/코엑스 제한/요청 가능 1곳을 검사한다. `expiredUnknownCanSupplementHeightAndOnlyConfirmRequestsEntrance`는 만료부터 보완·확인·거절까지 연결한다. 연결됐지만 속성이 누락되면 즉시 종료하며 재시도하지 않는 기존 검사도 유지한다.

이 정책은 API 호출을 강제 종료하거나 단일 executor의 막힌 읽기를 해제하지 않는다. UI 대기와 결과 수용의 상한이며 실제 Pleos 응답시간의 관측값이 아니다. 이 변경의 로컬 환경에는 Java·javac·Gradle 및 Android SDK가 확인되지 않아 JVM 테스트와 unsigned APK 빌드는 미실행이다. 새 커밋은 만들지 않았으며 새 후보의 기존 GitHub Actions 검증은 대기다. 새 전체 SHA와 `source-sha.txt`, `pleos-build-<full SHA>`, XML(실패·오류·건너뜀 모두 0), Gradle 로그, 입력 해시, unsigned APK를 결합해 검증해야 한다. README의 두 `sha256sum` 검사와 기존 패키지·ZIP·unsigned 검사 및 기준 SHA `a3f71cfb6e189a47fc8ebbe862c7247e01eeeb23` 대비 전체 diff도 필요하다. 기준 후보 성공은 새 후보 증거가 아니다.

이는 합성 검증 설계와 소스 변경이며 Windows 에뮬레이터·실제 SDK 콜백·실차·음성·음향의 실행 증거는 미확보다. 1차 필수 제안서·서명 동의서, 선택 프로토타입·적격 GitHub/APK, 최종 PT 작동 시연의 제출 단계 구분은 유지한다.


### 전기차 정책 누락 — ev-null (소스 대조일 2026-11-09)

`EvPolicyFallbackTest`는 합성 AAOS 연료 입력을 `VehicleProfile.fromHardware`부터 후보 계산·설명·EntranceConfirmation·fake 내비까지 연결한다. 실제 시설 정책 변경이나 SDK 수신 관측이 아니다. 현재 화면에는 시설의 전기차 정책을 주입하는 UI가 없다. 코엑스 복사본의 **전기차 정책만 null**로 바꾸며 높이·요금·입구·출처는 유지한다. 기존 공개 출처와 확인일은 SampleTrip 및 README 기록을 유지하며 외부 재확인은 하지 않았다.

| 합성 조건 | 기대 사용자 결과 / assertion |
| --- | --- |
| 높이 1850mm·정상 입구·차폭 조건 미등록·120분·구매 할인 충족 가정·NON_ELECTRIC | 부설 우선·요금 우선 모두 코엑스 0원 우선, 요청 가능 2곳 |
| 연료만 ELECTRIC으로 전환 | 두 기준 모두 대안 A 6,000원 우선, 요청 가능 1곳. 코엑스는 NEEDS_CHECK·요청 불가이며 기본 12,000원·계산된 예상 0원 유지 |
| 대안 설명 | ‘코엑스 지하주차장 · 전기차 입차 조건 미확인’. 누락을 입차 금지로 표현하지 않음 |
| 모든 후보 정책 null·ELECTRIC | 요청 가능 0곳, routeRequest·navigation.select·EntranceConfirmation.create 모두 비어 있음 |
| 독립 높이 초과·차폭 초과·명시적 전기차 제한 | 각각 RESTRICTED 유지. 입구 누락·좌표 오류도 별도 목록에서 선택·요청 차단 |

Lot의 두 생성자 모두 공통 record 생성 경계를 지나 null을 UNKNOWN으로 저장한다. ALLOWED·RESTRICTED·UNKNOWN은 그대로 보존한다. 높이 1850mm·정상 입구·차폭 조건 미등록의 정책 행렬에서 ELECTRIC은 ALLOWED만 요청 가능하고 null·UNKNOWN은 NEEDS_CHECK, RESTRICTED는 RESTRICTED다. 연료 누락·빈 배열·미확인 값·혼합 입력은 차량 NEEDS_CHECK를 유지하며 ALLOWED 시설만 요청 가능하다. NON_ELECTRIC은 전기차 정책만으로 차단하지 않는다.

비전기차 선택은 차량 조건 갱신에 대응하는 invalidateSelections 후 STALE이고 요청 0건이다. 전기차 계산·설명·대안 선택·확인창 생성까지도 0건이며 새 대안 A 확인만 원래 방문지 ‘스타필드 코엑스몰’과 시연 입구 37.51400, 127.06100으로 정확히 1건 요청한다. 되울림은 원래 방문지를 보존하고 중복 확인은 추가 요청하지 않는다. REJECTED와 예외는 실패 receipt로 기록한다. 기존 MainActivity의 이유 `setSingleLine(true)`와 상세의 차량값·출처·시설 provenance 경로는 유지한다.

신규 네 테스트는 기존 `app/src/test/java`에 있어 `testPleosDebugUnitTest` 대상이다. 생성자·정책 행렬·전환 통합·독립 차단 assertion을 기존 ParkingPlanTest, ParkingNavigationTest, EntranceConfirmationTest, VehicleWidthTest, EntranceCoordinateTest와 소스로 대조했다. 로컬 PATH에 Java·javac·Gradle이 없고 Android SDK도 확인되지 않아 JVM 테스트·unsigned APK 빌드는 미실행이다. 도구 설치·네트워크·자격 증명·CI 호출·커밋·푸시는 수행하지 않았다. 정적 검사는 실행 통과 증거가 아니다.

Oracle의 새 후보 커밋 후 기존 GitHub Actions에서 새 전체 SHA에 결합된 `testPleosDebugUnitTest`·`assemblePleosDebug` 성공, 기존·신규 XML의 실패·오류·건너뜀 모두 0을 확인해야 한다. `source-sha.txt` 및 `pleos-build-<full SHA>` 일치, Gradle 로그·입력 해시·unsigned APK, README의 `sha256sum -c apk-sha256.txt` 및 동일 SHA 체크아웃의 `sha256sum -c /path/to/input-sha256.txt`, 기존 APK 패키지·ZIP·unsigned 검사도 대기다. 기준 후보 성공으로 새 후보 증거를 대신하지 않는다.

기준 `041e2cb4bfbf97916813a56adeac2bbd294c39a8` 대비 작업 트리의 공백·전체 diff·신규 테스트 포함 네 editPaths 제한을 로컬 확인한다. 새 커밋이 없으므로 `git diff --check 041e2cb4bfbf97916813a56adeac2bbd294c39a8 HEAD`와 같은 범위 파일 제한·전체 diff의 새 후보 검증은 대기다. README 증거 계약·주행 제한·서명·권한·의존성·워크플로·게시 설정은 변경하지 않는다.

Windows 설치·화면·실제 SDK 콜백·차량 신호·실차·음성·음향·공개 배포 관측 증거는 없다. 합성 assertion·unsigned CI·실제 런타임·제출 완료는 별도 단계다. 1차 필수 제안서·서명 동의서 확보, 선택 프로토타입·적격 GitHub/APK, 최종 PT 필수 작동 앱 시연의 구분을 유지하며 이번 작업은 제출·시연 완료를 뜻하지 않는다.


## 이번 방문 시설 제외·되돌리기 (2026-11-09 소스 대조)

현행 조작: 시설 **상세 → 이번 방문에서 제외 → 대안 추천 → 제외 시설의 상세 → 제외 되돌리기**. 조작은 상세에만 있고 제외된 시설 행은 “이번 방문 제외”로 남는다. 상세에는 사용자 선택과 기존 차량 판정·차량값·시설 출처를 구분해 표시한다. 사용자 제외는 만차·폐쇄·시설의 명시적 입차 제한을 뜻하지 않는다. 첫 화면 이유는 코엑스 제외로 대안을 추천할 때 “코엑스 지하주차장 · 이번 방문 제외” 한 줄이다.

`VisitExclusionTest`는 실제 차량 신호가 아닌 합성 AAOS 입력을 `VehicleProfile.fromHardware`부터 fake 내비까지 연결한다. 부설 우선·요금 우선 모두 1850mm·NON_ELECTRIC·120분·구매 할인 조건 충족 가정에서 코엑스 예상 0원·요청 가능 2곳 → 코엑스 사용자 제외 시 대안 A 6,000원·1곳 → 되돌리면 원래 추천·2곳으로 복구된다. 제외해도 코엑스 MATCHES_RECORDED_RULES·기본 12,000원·예상 0원과 차량 판정 사유는 보존된다. 대안 A를 제외하고 높이만 2250mm로 바꾸면 코엑스 RESTRICTED·요청 가능 0곳이다. 대안 A를 되돌리면 6,000원 대안만 요청 가능하며 코엑스 높이 제한 판정은 유지된다. 두 요청 가능 시설 모두 제외해도 전체 3개 행은 남고 요청 가능 0곳이다.

제외는 같은 방문의 차량·요금·추천 기준 갱신과 요청 ID가 있는 동기·지연 입구 되울림에서 유지된다. 상관 ID 없는 새 목적지는 같은 ID·이름이어도 초기화한다. 이전 방문 상세의 조작과 현재 목록 밖 시설은 거부한다. 제외 상태는 Bundle에 저장하지 않으며 새 ParkingNavigation은 제외 없음으로 시작한다. 제외·복구는 선택을 만료시키고 화면을 갱신한다. 계산·설명·선택까지 요청 0건, 제외 전 Option 재전달도 차단하며 새 대안 A의 확인만 원래 방문지와 시연 입구 37.51400, 127.06100으로 1건 요청한다. 중복 확인은 추가 요청하지 않고 REJECTED·예외는 실패로 기록한다.

근거는 신규 테스트 assertion 및 MainActivity 상세 리스너·refresh·확인창·한 줄 이유 표시의 소스 대조다. 외부 시설 사실은 기존 SampleTrip 출처·확인일을 유지하며 이번 외부 재확인은 하지 않았다. 대안 A·입구 좌표는 시연값, 할인은 사용자 가정이며 실제 구매 검증이나 SDK 관측이 아니다.

이번 체크아웃의 로컬 Java·Gradle·Android SDK 실행 환경은 확인되지 않아 테스트와 APK 빌드 성공을 주장하지 않는다. 신규 테스트는 기존 app/src/test/java 아래의 testPleosDebugUnitTest 대상이다. 새 커밋 후 기존 GitHub Actions에서 testPleosDebugUnitTest·assemblePleosDebug와 높이·차폭·전기차 정책·입구 오류 회귀 XML을 확인해야 한다. 전체 XML 실패·오류·건너뜀 0, 새 전체 SHA의 source-sha.txt·pleos-build-<full SHA> 일치, README의 두 sha256sum 검사, 입력 해시·unsigned APK·패키지·ZIP·unsigned 검사와 기준 후보 대비 전체 diff는 아직 미확인이다. 기준 후보의 성공으로 대체하지 않는다. 이 작업에서는 커밋·푸시·네트워크·CI 변경을 하지 않는다.

Windows 설치·화면·실제 SDK 콜백·실차·음성·음향·공개 배포 증거는 없다. 합성 검증 설계·unsigned CI·실제 런타임 관측·제출 완료는 별도 단계다. 1차 필수 제안서·서명 동의서, 선택 프로토타입·적격 GitHub/APK, 최종 PT 필수 작동 앱 시연의 구분을 유지한다.


## 분 단위 체류 시간 (2026-11-09 소스 대조·합성 검증 설계)

현행 조작은 **시설 상세 → 요금 조건 → 체류 시간(분) 숫자 입력·구매 할인 가정 → 적용**이다. 현재 분 값을 표시하며 정수 1~360분은 시설 제한이 아닌 앱 내부 입력 정책이다. 빈값·0·음수·361·소수·문자·정수 넘침은 오류를 표시하고 창을 유지하며 기존 시간·할인 모두 보존한다. 취소는 변경하지 않는다. 이전 방문에서 연 창도 방문 토큰 검사로 적용을 거부한다. 시간은 같은 방문의 차량 재조회·추천 기준 변경·입구 되울림과 다음 방문에 유지하고 새 방문은 할인만 초기화한다. Bundle에는 분 값만 저장하며 범위 밖 복원값은 120분·할인 미적용이다. 추천 카드와 확인창은 90·121·241분을 버리지 않고 각각 ‘90분 예상’·‘121분 예상’·‘241분 예상’으로 표시한다. 정상 적용의 refresh는 이전 Selection을 만료시킨다. 차량값·출처는 상세에, 대안 이유는 최대 한 줄로 유지한다.

다음은 `StayDurationTest`에서 `VehicleProfile.fromHardware` → 공유 문자열 시간 적용 → 후보 계산·설명 → `EntranceConfirmation` → fake 내비를 연결하는 **기존 등록 요금식의 합성 기대값**이다. 실제 AAOS 신호나 SDK 콜백 관측이 아니다. 공통 조건은 1850mm·NON_ELECTRIC·구매 할인 조건 충족 가정이다. 대안·좌표는 기존 시연값이며 외부 시설 사실은 SampleTrip의 기존 출처·확인일을 유지한다. 이번에 외부 재확인은 하지 않았다.

| 입력 분 | 코엑스 예상 | 대안 A 예상 | 부설 우선 / 요금 우선 추천 |
| --- | --- | --- | --- |
| 120 | 0원 | 6,000원 | 모두 코엑스 |
| 121 | 1,500원 | 6,500원 | 모두 코엑스 |
| 180 | 6,000원 | 9,000원 | 모두 코엑스 |
| 240 | 12,000원 | 12,000원 | 동률로 모두 코엑스 |
| 241 | 13,500원 | 12,500원 | 코엑스 / 대안 A (‘요금 우선 · 예상 요금이 낮은 대안’) |

180분에서 높이만 1850→2250mm로 바꾸면 두 기준 모두 코엑스 RESTRICTED·선택 불가, 대안 A 9,000원·요청 가능 1곳이다. 1850mm·ELECTRIC이면 코엑스 NEEDS_CHECK이며 높이 누락은 요청 가능 0곳이다. 시간 입력·계산·설명·확인 모델 생성까지 요청 0건이다. 241분·요금 우선의 새 대안 A 확인창은 ‘241분 예상 12,500원’을 표시한다. 확인 후 원래 방문지와 37.51400, 127.06100으로 정확히 1건 요청하며 중복 확인은 추가 요청하지 않는다. 거절·예외는 실패다. 입력 경계 1·90·121·180·241·360, int 범위 우회 차단, 잘못된 복원값, 취소·오래된 창·복원 상태 보존도 신규 assertion으로 대조한다.

로컬 PATH에 java·javac·gradle이 없고 알려진 Android SDK 경로도 없어 `testPleosDebugUnitTest`·`assemblePleosDebug`는 미실행이다. 신규 테스트는 기존 app/src/test/java 경로에 있어 해당 단위 테스트 대상이다. 소스 대조와 작업 트리 공백 검사는 실행 통과 증거가 아니다. 커밋·푸시·네트워크·자격 증명·도구 설치·CI 변경은 하지 않는다. Oracle이 새 후보를 커밋한 뒤 기존 GitHub Actions에서 새 전체 SHA와 `source-sha.txt`·`pleos-build-<full SHA>` 일치, 테스트·빌드 성공과 기존·신규 XML(차폭·좌표·제외 회귀 포함)의 실패·오류·건너뜀 모두 0을 확인해야 한다. Gradle 로그·입력 해시·unsigned APK, README의 두 sha256sum 검사 및 기존 APK 패키지·ZIP·unsigned 검사도 대기다. 기준 `bcb60a1176d1a1f9617b89cf8f8370c1233f68d6` 대비 새 HEAD 전체 diff·`git diff --check`·지정 5개 editPaths 제한을 확인해야 하며 기준 후보 성공으로 대체하지 않는다.

Windows 설치·화면·실제 SDK 콜백·차량 신호·실차·음성·음향 증거는 없다. 합성 검증 설계·unsigned CI·실제 런타임·제출 완료는 별도 단계다. 1차 필수 제안서·서명 동의서, 선택 프로토타입·적격 GitHub/APK, 최종 PT 필수 작동 앱 시연의 구분을 유지하며 제출·시연 완료를 주장하지 않는다.


## 후보 발급 세대와 재선택 차단 (option-epoch, 2026-11-09)

`ParkingNavigation.options`의 모든 오버로드는 공통 발급 경계를 거친다. 현재 내비 인스턴스·generation에서 발급한 **Option 객체 동일성**을 보관하며 같은 세대의 반복 계산은 앞서 발급한 후보도 유지한다. 조건 갱신의 `invalidateSelections`, 새 방문, 제외·되돌리기는 발급 집합을 비운다. 이전 Selection의 확인은 STALE이고, 이전 Option으로 `select` 또는 `EntranceConfirmation.create`를 호출해 **새 확인을 만드는 것**도 거부한다. 이는 서로 다른 차단이다. equals가 같은 복제·다른 내비 발급·직접 compare 후보도 선택할 수 없다. 요청 ID가 연결된 동기·지연 입구 되울림은 조기 반환하여 원래 방문과 후보 세대를 유지한다.

다음은 `OptionGenerationTest`의 합성 AAOS `VehicleProfile.fromHardware` → 후보 → 설명·확인 모델 → fake 내비 계약 사례다. 화면에서 과거 후보를 주입하는 사용자 조작이나 Android 화면 재현, 실제 차량 신호·SDK 콜백 관측을 뜻하지 않는다. 외부 시설 정보·출처·확인일과 시연 좌표는 기존 SampleTrip/README 기록을 유지하며 외부 재확인은 하지 않았다.

| 합성 입력·전이 (120분·구매 할인 충족 가정) | assertion 기대값 |
| --- | --- |
| 1850mm·NON_ELECTRIC | 두 추천 기준 모두 코엑스 0원 우선·요청 가능 2곳. Option과 Selection 보관 |
| invalidateSelections 직후, 새 목록 계산 전 | 이전 Option select·확인 모델 생성은 비어 있음, 이전 Selection 확인은 STALE, 요청 0건 |
| 높이만 2250mm로 변경 | 두 기준 모두 코엑스 RESTRICTED·요청 불가, 대안 A 6,000원·요청 가능 1곳. 과거 코엑스 Option 재선택 불가 |
| 1850mm·ELECTRIC / 높이 누락 | 코엑스 NEEDS_CHECK·요청 불가 / 전체 요청 가능 0곳. 과거 Option으로 우회 불가 |
| 갱신 후 동일 값 재계산 / 같은 시설 객체의 새 방문 | equals상 같은 이전 객체는 거부, 새 발급 객체만 선택 가능. 같은 세대 반복 계산의 정상 후보는 모두 선택 가능 |
| 2250mm 새 대안 계산·설명·확인 모델 생성 | 요청 0건. 명시적 확인 후 원래 방문지 ‘스타필드 코엑스몰’과 37.51400, 127.06100으로 정확히 1건. 중복 확인 STALE·추가 요청 없음 |
| fake REJECTED·예외 / SIMULATED | 실패 / 시연 결과로 유지. 실제 내비 수락으로 표시하지 않음 |

MainActivity는 변경하지 않는다. 차량값·출처는 상세 배치, 대안 이유는 최대 한 줄을 유지한다. 신규 테스트는 기존 `app/src/test` JUnit 대상이며 `testPleosDebugUnitTest`에 포함된다. 기존 `ParkingNavigationTest`의 방문·실패·되울림 계약도 함께 검증해야 한다.

로컬 환경 확인(2026-11-09): Java·javac·Gradle 실행 경로와 Android SDK 환경 설정을 찾지 못해 설치 없이 소스 대조·diff 검사만 수행한다. 테스트 assertion은 실행 성공 증거가 아니다. 커밋·네트워크 작업은 수행하지 않으므로 새 전체 SHA 및 새 후보 Actions 증거는 대기다. 후속 새 커밋의 `testPleosDebugUnitTest`·`assemblePleosDebug` 성공, 기존·신규 XML의 실패·오류·건너뜀 모두 0, `gradle.log`, `source-sha.txt`와 `pleos-build-<full SHA>` 일치, 입력·APK 해시의 README 두 `sha256sum -c`, 기존 APK 패키지·ZIP·unsigned 검사를 확인해야 한다. 기준 성공으로 대체하지 않는다. 기준 `1e58b24f57548c5003b89e76f21e1a2415802245` 대비 작업 트리의 전체 diff·공백·지정 네 editPaths를 검사하며, 최종 `git diff --check 1e58b24f57548c5003b89e76f21e1a2415802245 HEAD`와 같은 범위 파일 검사는 새 커밋 이후 대기다.

Windows 설치·화면·실제 SDK 콜백·실차·음성·음향 관측 증거는 없다. 합성 계약 검증과 실제 런타임·제출 완료는 별도다. 기존 제출 단계 표의 **1차 필수 제안서·서명 동의서**, **선택 프로토타입·적격 GitHub/APK**, **최종 PT 필수 작동 앱 시연** 구분을 유지하며 이번 변경으로 제출·시연 완료를 주장하지 않는다.


## 동일 Activity 복귀와 루프박스 총높이 (소스 대조 2026-11-09)

`VehicleHeight.start()`는 새 입력창 토큰을 발급하지만 같은 객체의 확인값은 유지한다. `stop()` 중에는 적용·삭제할 수 없고 복귀 후에도 이전 토큰은 거부한다. 현재 토큰의 **보완값 삭제**만 확인값을 지운다. 새 Activity는 새 VehicleHeight를 생성하므로 미입력이다. 총높이는 Bundle에 저장하지 않는다. 같은 Activity 복귀만으로 방문·체류 시간·구매 할인·추천 기준·제외 상태를 초기화하지 않는다. 원본 차량은 복귀마다 UNKNOWN에서 새 조회를 시작하며 이전 Option 재선택과 Selection 확인은 차단한다.

다음은 `VehicleRefreshStateTest.sameScreenReturnKeepsRoofHeightAcrossFreshReadFailureAndExpiry`의 합성 입력·가짜 시계 기대값이다. AAOS 형태의 `fromHardware` 입력 1850mm·NON_ELECTRIC, 120분·구매 할인 가정, 사용자 확인 총높이 2250mm 적용 후 같은 Screen의 stop→onStart를 연결한다. 부설 우선·요금 우선 모두 아래와 같다. Android 화면이나 실제 SDK 콜백을 관측한 기록이 아니다.

| 복귀 후 새 조회 | 원본 / 사용자 확인 / 적용 높이 | 기대 결과 |
| --- | --- | --- |
| 1850mm·NON_ELECTRIC | 1850 / 2250 / 2250mm | 코엑스 RESTRICTED·선택 불가, 대안 A 6,000원 우선·요청 가능 1곳 |
| 2600mm·NON_ELECTRIC | 2600 / 2250 / 2600mm | 코엑스 RESTRICTED, 요청 가능 0곳 |
| 조회 실패 또는 3000ms 만료 | null / 2250 / 2250mm, 연료 NEEDS_CHECK | 코엑스 RESTRICTED, 대안 A 6,000원만 요청 가능 |
| 1850mm 재조회 후 현재 토큰으로 명시적 삭제·갱신 | 1850 / 미입력 / 1850mm | 코엑스 0원 우선·요청 가능 2곳 |

상세는 원본 차량값·출처, 사용자 확인값·출처, 적용값을 구분하고 첫 화면의 대안 이유는 최대 한 줄을 유지한다. 복귀·재조회·계산·설명·확인 모델 생성까지 fake 요청은 0건이며 새 대안 A 확인만 원래 방문지와 시연 입구 37.51400, 127.06100으로 1건 요청한다. 중복 확인은 추가 요청하지 않는다. REJECTED·예외는 실패이고 SIMULATED는 시연 처리다. 이전 읽기 결과·타이머는 새 상태를 바꾸지 않는다. 반복 복귀·이전 토큰 거부·명시적 삭제·새 객체 초기화는 `VehicleHeightTest`에서 별도로 검사한다. 기존 `OptionGenerationTest`·`ParkingNavigationTest`도 새 후보 CI 검증 대상이다.

이번 작업 환경에는 PATH의 Java·Gradle과 Android SDK 설정이 없어 로컬 JVM 테스트·APK 빌드를 실행하지 못했다. 도구 설치·네트워크·커밋·푸시는 수행하지 않는다. 소스 assertion과 공백 검사는 실행 XML을 대신하지 않는다. 새 커밋이 만들어진 뒤 기존 GitHub Actions에서 `testPleosDebugUnitTest`·`assemblePleosDebug`, 전체 XML의 실패·오류·건너뜀 각 0건을 확인해야 한다. `source-sha.txt`와 `pleos-build-<full SHA>`의 새 전체 SHA 일치, README의 두 `sha256sum -c` 검사, APK 패키지·ZIP·unsigned 검사와 기준 `fc3bd6fe09c98370ec8e3d1aebc1c5169ce05a40` 대비 전체 diff·지정 다섯 파일 제한도 대기다. 기준 후보의 성공은 새 후보 성공 근거가 아니다.

Windows 설치·화면·Activity 수명주기, 실제 SDK 콜백·차량 신호·실차·음성·음향 증거는 미확보다. 외부 사실은 기존 출처·확인일을 유지하며 이번에는 재확인하지 않았다. 1차 필수 제안서·서명 동의서, 선택 프로토타입·적격 GitHub/APK, 최종 PT 필수 작동 앱 시연은 별도 준비 대상이다.


## 속성 형식 오류 격리 — prop-isolate (2026-11-09)

`VehiclePropertyReader`는 플랫폼 객체를 만들지 않는 합성 source 경계다. 제조사·모델·연식·치수·연료를 각각 한 번 읽고 응답 null, 사용 불가 상태, 값 null, 잘못된 타입 및 개별 RuntimeException을 해당 속성의 누락으로만 처리한다. 문자열 숫자나 다른 배열 타입은 변환하지 않는다. Android 매핑 소스 대조에서는 `AndroidVehicleInfo`의 연결된 manager가 기존 INFO_MAKE/String, INFO_MODEL/String, INFO_MODEL_YEAR/Integer, INFO_EXTERIOR_DIMENSIONS/Integer[], INFO_FUEL_TYPE/Integer[]와 global area 0을 사용하며 STATUS_AVAILABLE 값만 reader에 전달한다. 연결 판정·정리·바깥 실패 처리는 유지한다. 이는 소스 대조이며 실제 SDK 응답 관측이 아니다.

`VehiclePropertyReaderTest`의 가짜 source를 공유 reader → CONNECTED → VehicleRefreshState의 COMPLETE → 후보 계산·설명 → EntranceConfirmation → fake 내비까지 연결한다. 개별 오류는 자동 재시도를 만들지 않는다. 아래는 신규 assertion의 합성 기대값이며 오류 주입 UI나 실행 관측 기록이 아니다. 모두 120분·구매 할인 조건 충족 가정·제외 없음이며 부설 우선/요금 우선 양쪽을 검사한다.

| 합성 source 입력 | 사용자 결과 및 assertion |
| --- | --- |
| 모델만 Integer, 치수 1850mm·1990mm, UNLEADED | 모델만 누락, 정상 높이·너비·비전기차 보존. 코엑스 0원 우선, 요청 가능 2곳 |
| 위 입력에서 높이만 2250mm | 코엑스 RESTRICTED·선택 불가, 대안 A 6,000원 우선, 요청 가능 1곳. 대안 이유는 높이 초과 |
| 제조사 보완 대상 아닌 차량의 치수가 사용 불가 또는 다른 배열 타입 | 높이·너비 null, 확인 필요, 요청 가능 0곳 |
| 높이 1850mm, 연료만 잘못된 타입 | NEEDS_CHECK, 코엑스 확인 필요, 대안 A 6,000원만 요청 가능. 정상 ELECTRIC은 전기차로 유지 |
| HYUNDAI·IONIQ 5 N·2025, 치수 누락 | 기존 제조사 보완 1585mm·1940mm 및 출처 표시 유지. 식별값 하나의 형식 오류면 보완 없음. 정상 하드웨어 1850mm·1990mm가 있으면 하드웨어 우선 |

속성 읽기·계산·설명·확인 모델 생성까지 요청 0건이다. 1850→2250mm 갱신으로 이전 Option 선택과 Selection 확인을 차단하고 새 대안 A 확인만 원래 방문지 및 시연 좌표 37.51400, 127.06100으로 1건 요청한다. 중복 확인은 추가 요청하지 않으며 ACCEPTED·REJECTED·예외에 의한 REJECTED·SIMULATED를 구별한다. 차량값·출처의 상세 배치와 대안 이유 최대 한 줄은 유지하며 MainActivity 변경은 없다.

이번 체크아웃에는 Java·Gradle이 PATH에 없어 JVM 테스트와 APK 빌드는 실행하지 못했다. 도구 설치·커밋·푸시·네트워크 호출은 하지 않았다. 신규 테스트는 실행 전 assertion이며 기존 성공 후보로 대체 증명하지 않는다. 새 후보 커밋의 GitHub Actions에서 `testPleosDebugUnitTest`·`assemblePleosDebug`, 신규 VehiclePropertyReaderTest 및 기존 VehicleProfileTest·VehicleWidthTest·VehicleReadResultTest를 포함한 전체 XML의 실패·오류·건너뜀 0건을 확인해야 한다. 새 전체 SHA와 `source-sha.txt`·`pleos-build-<full SHA>` 일치, Gradle 로그·입력 해시·unsigned APK, README의 두 sha256sum 검사와 기존 패키지·ZIP·unsigned 검사, 기준 `53aed5c98ca707b486f12e74289150cc4f6ae088` 대비 새 HEAD 전체 diff·공백 검사·다섯 editPaths 제한은 새 후보 CI 증거 대기다.

외부 사실은 기존 출처·확인일을 유지하며 이번에는 재확인하지 않았다. Windows 설치·화면·실제 SDK 콜백·차량 신호·실차·음성·음향의 관측 증거는 없다. 합성 source 검증 설계, Android 매핑 소스 대조, CI 실행 증거, 실제 런타임 관측을 구별한다. 1차 필수 제안서·서명 동의서, 선택 프로토타입·적격 GitHub/APK, 최종 PT 필수 작동 앱 시연은 별도 준비 대상이며 제출·시연 완료를 주장하지 않는다.

## 구매 할인 조건 누락 검증 (discount-proof)

`DiscountRuleTest`의 합성 기대값이다. 실제 시설 정책 변경이나 오류 주입 UI, SDK 콜백 관측 기록이 아니다. 코엑스 복사본의 구매 할인 설명만 null·빈 문자열·공백으로 바꾸고 기존 무료 120분은 유지한다. 조건 설명은 null을 빈 문자열로, 앞뒤 공백은 제거해 정규화한다. 할인 가정·양수 무료 시간·비어 있지 않은 조건을 모두 만족해야 차감한다. 조건 누락은 입차 제한이 아니며 등록 기본요금으로 비교한다.

| 합성 AAOS 형태 입력·120분·구매 할인 가정 | 계산·추천·확인 기대값 |
| --- | --- |
| fromHardware: 1850mm·NON_ELECTRIC, 조건 누락 | 코엑스 기본·예상 모두 12,000원·MATCHES_RECORDED_RULES, 대안 A 6,000원, 요청 가능 2곳. 부설 우선은 코엑스, 요금 우선은 대안 A. 요금 우선 이유는 ‘요금 우선 · 예상 요금이 낮은 대안’ |
| 같은 목록에서 높이만 2250mm | 두 기준 모두 코엑스 RESTRICTED·선택 불가, 대안 A 6,000원 우선·요청 가능 1곳. 이유는 ‘코엑스 지하주차장 · 높이 초과’ |
| 1850mm·ELECTRIC / 높이 누락·NON_ELECTRIC | 전자는 코엑스 NEEDS_CHECK·선택 불가, 후자는 요청 가능 0곳·모두 선택 불가 |
| 정상 조건 설명 | 120분 할인 0원, 121분 할인 1,500원, 할인 미선택 120분 12,000원. 두 기준 모두 120분 코엑스 우선 |

시설 상세와 입구 확인창은 같은 `Tariff.discountText`를 사용한다. 양수 무료 시간에 조건이 없으면 **구매 할인 조건 미확인 · 할인 미적용**, 코엑스 확인 금액은 **120분 예상 12,000원**이다. 무료 시간 0인 대안 A는 **적용할 구매 할인 규칙 없음**, 확인 금액은 **120분 예상 6,000원**으로 구분한다. 정상 단가 0은 유효하며 기존 잘못된 요금·넘침의 미확인 처리는 유지한다. 차량값·출처는 상세에, 대안 이유는 최대 한 줄로 유지하며 첫 화면 구성은 유지한다.

계산·설명·확인 모델 생성까지 fake 요청은 0건이다. 높이 갱신에 대응하는 무효화 후 이전 선택은 STALE이며 새 대안 A의 명시적 확인만 원래 방문지 ‘스타필드 코엑스몰’과 시연 좌표 **37.51400, 127.06100**으로 1건 요청한다. 입구 되울림은 원래 방문지를 유지하고 중복 확인은 추가 요청하지 않는다. ACCEPTED·REJECTED·예외에 의한 REJECTED·SIMULATED는 각각 구분한다.

외부 시설 공개값·출처·확인일은 `SampleTrip`의 기존 **2026-11-08** 기록을 유지하며 이번에 외부 재확인은 하지 않았다. 조건 누락 복사본·차량 입력은 합성 검증, 대안 A와 좌표는 시연값, 할인은 구매 검증이 아닌 가정이다. 신규 테스트는 기존 `app/src/test` JUnit 대상이므로 `testPleosDebugUnitTest`에 포함된다. `TariffFallbackTest`·`ParkingNavigationTest`를 포함한 전체 XML은 새 후보 CI에서 확인해야 한다.

이 체크아웃의 로컬 Java·Gradle·Android SDK 실행 환경은 확보되지 않아 테스트·APK 성공을 주장하지 않는다. 커밋·push·네트워크 실행은 이 작업 범위에서 금지되어 새 SHA의 GitHub Actions 검증은 대기다. 후속 새 커밋에서 기존 unsigned debug CI의 `testPleosDebugUnitTest`·`assemblePleosDebug`, 전체 XML 실패·오류·건너뜀 0, Gradle 로그·입력 해시·unsigned APK를 확인한다. `source-sha.txt`와 `pleos-build-<full SHA>`가 새 전체 SHA에 결합되어야 하며 README의 두 sha256sum 검사와 APK 패키지·ZIP·unsigned 검사, 기준 `acbe031b64ea7f0b82dc973459a06244373f646e` 대비 전체 diff·경로 범위를 대조한다. 기준 SHA 성공으로 대체하지 않는다. Windows 에뮬레이터·실차·실제 Pleos 런타임·Gleo 발화·음향 증거는 없다.

제출 준비 구분은 유지한다. 1차 필수 제안서·서명 동의서, 선택 제출물, 최종 PT 작동 시연은 별도 준비·확인 대상이며 합성 assertion이나 unsigned debug CI가 실제 작동 시연을 대신하지 않는다.


## 시설 높이 오류의 미확인 처리 (2026-11-09 소스 대조)

`FacilityHeightTest`의 합성 assertion 기대값이다. 실제 코엑스 제한 변경, 오류 주입 UI 또는 SDK 실행 관측이 아니다. `Lot`의 두 생성자는 공통 생성 경계에서 시설 높이 null·0·-1·Integer.MIN_VALUE를 null로 저장한다. 양수 1·2200·2500·Integer.MAX_VALUE는 그대로 보존하며 단위 추정·임의 상한은 없다. 요금·입구·전기차 정책·차폭 조건·출처는 보존한다.

합성 `VehicleProfile.fromHardware`부터 실제 후보 계산의 evaluate·requestAvailability, 추천 설명, EntranceConfirmation과 fake 내비를 연결한다. 공통 조건은 120분·구매 할인 조건 충족 가정·제외 없음이며 부설 우선과 요금 우선 모두 검사한다.

| 합성 시설·차량 입력 | 판정·금액·사용자 결과 |
| --- | --- |
| 코엑스 복사본 높이만 null·0·-1·Integer.MIN_VALUE, 차량 1850mm·NON_ELECTRIC | 코엑스 NEEDS_CHECK·요청 불가, 기본 12,000원·예상 0원 유지. 대안 A 6,000원 우선·요청 가능 1곳. 이유는 ‘코엑스 지하주차장 · 높이 여유 또는 제한 정보 확인 필요’이며 높이 초과로 표시하지 않음 |
| 정상 시설 높이 2200mm, 차량 1850 / 2200 / 2250mm | 각각 MATCHES_RECORDED_RULES / NEEDS_CHECK / RESTRICTED. 경계와 실제 높이 초과 판정 유지 |
| 잘못된 시설 높이 + 별도 명시적 전기차 제한 / 최대 허용 차폭 1980mm에 차량 1990mm | 각각 RESTRICTED·요청 불가. 높이 미확인이 다른 명시적 제한을 약화시키지 않음 |
| 대안 A 높이만 0, 차량 1850→2250mm·NON_ELECTRIC | 요청 가능 후보 1→0곳. 코엑스는 등록 높이 초과로 RESTRICTED, 대안 A는 NEEDS_CHECK. 갱신 후 이전 선택 STALE, 새 후보 모두 선택 불가·요청 0건 |

잘못된 코엑스 높이와 정상 대안 A의 계산·설명·확인 모델 생성까지 fake 요청은 0건이다. 대안 A 확인 문구는 120분 예상 6,000원이며 적용할 구매 할인 규칙이 없음을 유지한다. 명시적 확인만 원래 방문지 ‘스타필드 코엑스몰’과 시연 좌표 **37.51400, 127.06100**으로 정확히 1건 요청한다. 입구 되울림은 원래 방문지를 유지하고 중복 확인은 추가 요청하지 않는다. ACCEPTED, 거절·예외의 REJECTED, 시연 결과 SIMULATED를 구별한다.

MainActivity는 변경하지 않는다. 기존 상세의 null 높이 표시는 ‘미확인’, 후보 행은 ‘확인 필요’를 사용한다. 차량값·출처는 상세에, 대안 이유는 최대 한 줄로 유지한다. 외부 공개값·출처·확인일은 SampleTrip의 기존 **2026-11-08** 기록을 유지하며 외부 재확인은 하지 않았다. 시설 오류 복사본·차량 입력은 합성, 대안·좌표는 시연값, 할인은 구매 검증이 아닌 가정이다.

로컬 PATH에 Java·javac·Gradle이 없고 Android SDK 설정도 확인되지 않아 JVM 테스트·APK 빌드는 미실행이다. 소스 assertion 대조와 작업 트리 공백 검사는 실행 성공 증거가 아니다. 도구 설치·커밋·푸시·네트워크·자격 증명·CI 변경은 수행하지 않는다. Oracle에서 새 후보 커밋을 만든 뒤 기존 GitHub Actions의 `testPleosDebugUnitTest`·`assemblePleosDebug` 성공과 FacilityHeightTest·ParkingPlanTest·VehicleWidthTest·ParkingNavigationTest를 포함한 전체 XML의 실패·오류·건너뜀 각각 0을 확인해야 한다. 새 전체 SHA와 `source-sha.txt`·`pleos-build-<full SHA>` 일치, Gradle 로그·입력 해시·unsigned APK, README의 두 `sha256sum -c` 및 APK 패키지·ZIP·unsigned 검사는 대기다. 기준 `6103153261e1a9c5c746a45f5c4abe3245e62a22` 대비 새 HEAD 전체 diff·`git diff --check`·지정 네 editPaths 제한도 새 커밋에서 확인해야 한다. 기준 후보 성공으로 대체하지 않는다.

합성 검증 설계·unsigned CI·실제 런타임·제출 완료를 구분한다. Windows 에뮬레이터 설치·화면·실제 SDK 콜백·차량 신호·실차·Gleo 발화·음향 증거는 미확보다. 1차 필수 제안서·서명 동의서, 선택 프로토타입·적격 GitHub/APK, 최종 PT 필수 작동 앱 시연은 별도 준비·확인 대상이다.
