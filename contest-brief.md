# 주차까지 · 공모전 기획 초안

**현재 후보.** Asta Sans를 번들한 Android 화면에서 주차장 이름, 요금과 입구 안내를 보여준다. AAOS에서 읽은 차량 정보로 후보를 다시 계산하고, 조회를 지원하지 않는 차는 정확한 공식 제원이 있는 경우에만 이를 보완값으로 사용한다. 실제 차량에서 어떤 속성을 사용할 수 있는지는 런타임 확인 대상이며 목적지와 경로 요청은 여전히 시연용이다.

**문제.** 운전자는 목적지를 내비에 입력한 뒤에도 주차장 유무, 입차 조건과 방문 할인을 알아보려고 휴대폰에서 같은 장소를 다시 찾는다. 낯선 식당, 병원, 전시장에 갈 때마다 반복되는 일이다.

**제안.** 현재 내비 목적지에 연결된 부설·제휴 주차장을 확인하고, 차량 조건과 체류 시간에 맞춰 주변 대안과 함께 보여준다. 선택이 끝나면 실제 차량 입구를 기본 내비의 도착점으로 넘긴다. 원래 방문 장소와 차량 입구는 별도로 저장한다.

**첫 구현.** 코엑스 사례 하나와 명시한 시연 대안 두 곳. 높이 제한, 전기차 입차 정책, 요금, 방문 할인 조건, 입구 좌표를 비교한다. 정보가 없으면 확인 필요로 표시하고, 확인이 필요한 입구에는 경로 요청을 만들지 않는다.

**연동 가설.** NaviHelper의 `getDestinationInfo()`와 `requestRoute()`를 먼저 검증한다. 차량 제원은 지원 여부를 확인한 차량 식별 정보와 출처 있는 제원표 또는 사용자 등록 높이를 연결한다. 전기차 입차, 충전 가능 여부, 감면 자격은 별개 속성으로 취급한다. [NaviHelper](https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/destinations) · [Pleos 차량 정보 가용성](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability)

**검증할 가치.** 자주 찾는 목적지 다섯 곳에서 앱 한 화면으로 판단을 끝낼 수 있는지 관찰한다. 공공데이터로 위치와 요금의 범위를 넓히고, 입구 높이와 할인은 운영 주체의 안내를 확인해 관리할 수 있는지 살핀다. [전국주차장정보표준데이터](https://www.data.go.kr/data/15012896/standard.do)

## 사업성 검증안 · 시설 정보 등록과 유지

작성일: **2026-11-08**. 목적과 근거: **사용자 제공 심사 기준(수령일 2026-11-08)**의 사업성 검토를 위해, 1차 제안서에 누가 무엇을 구매하고 정보 유지 비용을 감당할 수 있는지 검증할 계획을 추가한다. 심사 안내의 외부 재확인이나 조사 수행 결과가 아니다.

| 항목 | 검증할 가설 |
| --- | --- |
| 초기 이용자 | 낯선 시설을 방문하는 루프박스 장착 차량·전기차 운전자. 높이와 전기차 입차 조건을 확인해야 하는 방문자로 좁힌다. |
| 지불 주체 | 주차시설 운영자. 정보 관리 서비스를 구매할 의향이 있다는 것은 검증 전 가설이다. |
| 유료 제공물 | 시설별 입구·높이 및 전기차 제한·방문 할인 정보의 최초 등록, 변경 접수, 정기 재확인과 미확인 표시를 묶은 월 유지 서비스. |
| 목표 사용자 결과 | 차량 조건에 맞는 주차장과 입구를 고를 때 목적지를 다시 검색하는 비율을 줄인다. 실제 감소는 아직 측정하지 않았다. |
| 운영자 편익 | 반복 문의와 잘못된 입구 진입 안내 부담을 줄일 수 있다는 검증 전 가설이다. 매출 증가나 비용 절감 실적을 뜻하지 않는다. |

### 정보 운영 책임과 비용 가정

다음은 향후 운영 설계이며 담당자 배정·제휴 확보 사실이 아니다. 출처·확인일·확인 주체를 항목별로 기록하고, 데이터 누락과 시설의 명시적 제한을 별도로 관리한다.

| 작업 | 담당 역할과 처리 책임 | 비용 반영 항목 |
| --- | --- | --- |
| 최초 확인 | 서비스 정보 담당자가 공개 안내와 운영자 답변을 대조하고 입구·제한·할인별 근거를 기록한다. 시설 운영 담당자는 원자료 확인을 맡는다는 가정이다. 확인 못한 항목은 등록된 사실로 확정하지 않는다. | 서비스 측 최초 확인 2시간/시설 × 20,000원/시간 = 40,000원/시설. 첫 12개월에 배분해 월 작업 시간에 2/12시간을 포함한다. 운영자 응답 시간은 별도 조사하며 아래 서비스 측 원가에는 포함하지 않는다. |
| 변경 접수 | 서비스 접수 담당자가 운영자 또는 이용자의 변경 제보를 기록하고, 정보 담당자가 근거 확인 후 반영한다. 제보만으로 확정하지 않는다. | 월 유지 작업 시간 중 변경 접수·검토 0.15시간/시설(부담 증가 시 0.60시간/시설); 연락·접수 도구 비용은 기타 변동비에 포함한다. |
| 재확인 | 서비스 정보 담당자가 월 1회 근거와 변경 여부를 재확인하고, 시설 운영 담당자에게 불일치 확인을 요청한다. | 월 유지 작업 시간 중 0.20시간/시설(부담 증가 시 0.50시간/시설); 모든 서비스 작업에 동일 시간당 비용을 적용한다. |
| 미확인 처리 | 서비스 정보 담당자가 무응답·근거 부족 항목을 미확인으로 표시하고 재문의 대기 상태로 둔다. 명시적 입차 금지와 구분하며 확인이 필요한 입구의 경로 요청을 허용하지 않는 기존 원칙을 따른다. | 월 유지 작업 시간 중 0.15시간/시설(부담 증가 시 0.40시간/시설); 표시·재문의 기록 작업을 포함한다. |

위 운영 표와 아래 비용표의 **모든 수치·주기는 2026-11-08 작성 계산 가정**이며 시장 가격·실측 작업량이 아니다. 유지 작업 시간은 최초 확인 이후 월 반복 시간이고, 월 원가에는 최초 확인 배분 시간도 더한다. 동일 역할을 한 사람이 겸할 수 있으나 작업 시간을 중복 계산하지 않는다.

데이터 대조 근거: [`SampleTrip.java`](app/src/main/java/kr/juchakkaji/app/SampleTrip.java)의 출처 표시(로컬 소스 확인일 **2026-11-08**). 코엑스 높이·승용차 요금·10만 원 할인은 소스에 공식 안내와 2026-11-08 확인일이 기록된 공개값이며 이번 작업에서 외부 재확인하지 않았다. 코엑스 입구 좌표는 시연값, 전기차 입차 조건은 미확인이다. 대안 A의 높이·요금·전기차 조건·입구 좌표와 대안 B의 요금은 가상 시연값이며 B의 높이·입구 정보는 없다. 이를 실제 확보한 제휴 데이터나 운영자가 검수한 정보로 취급하지 않는다.

### 월 단위 경제성 계산

서비스 제공자 관점의 첫 12개월 월평균 모델이다. 초기 확인 비용은 현금으로 먼저 발생하므로 배분 계산과 별도로 자금이 필요하다. 세금·초기 앱 개발 투자·고객 확보 비용은 제외한 제한된 운영 손익 가정이며 사업 전체 수익성을 확정하지 않는다. 월 고정비는 공통 운영 인력·도구 비용, 기타 변동비는 시설별 연락·저장 비용으로 가정하고 위 직접 작업 인건비와 중복하지 않는다.

| 입력 또는 산출 (모든 입력: 2026-11-08 가정) | 기본 | 유지 부담 증가 |
| --- | --- | --- |
| 시설당 월 요금 P | 30,000원/시설·월 | 30,000원/시설·월 |
| 월 유지 작업 시간 M | 0.50시간/시설·월 (= 0.15 + 0.20 + 0.15) | 1.50시간/시설·월 (= 0.60 + 0.50 + 0.40) |
| 최초 확인 배분 시간 I | 2/12시간/시설·월 | 2/12시간/시설·월 |
| 원가 적용 작업 시간 T = M + I | 2/3시간/시설·월 | 5/3시간/시설·월 |
| 시간당 비용 W | 20,000원/시간 | 20,000원/시간 |
| 기타 변동비 V | 2,000원/시설·월 | 3,000원/시설·월 |
| 월 고정비 F | 600,000원/월 | 600,000원/월 |
| 시설당 월 공헌이익 C = P − T × W − V | 44,000/3원/시설·월 (약 14,666.67원) | −19,000/3원/시설·월 (약 −6,333.33원) |
| 손익분기 시설 수 N | ceil(F/C) = ceil(450/11) = **41시설** | **유한한 손익분기점 없음** (C ≤ 0) |

반올림 전 분수로 계산한다. 월 운영손익 = N × C − F. 기본은 40시설에서 −40,000/3원/월, 41시설에서 4,000/3원/월이다. 유지 부담 증가에서는 시설을 늘릴수록 손실이 커지므로 가격·작업 절차를 다시 검증해야 한다.

### 후속 조사 설계 · 아직 수행하지 않음

아래 표본 수·판정 수치는 모두 **2026-11-08 작성 잠정 조사 설계**다. 모집·인터뷰·실제 시연은 이번 작업에서 수행하지 않는다. 통과는 다음 검증 단계로 진행하는 조건일 뿐 사업성 입증이 아니며, 중간 범위는 조건을 수정해 재조사한다. 필수 지표 중 하나라도 중단 기준에 해당하면 현재 가격·운영안의 확대를 중단한다.

| 지표 | 측정 대상·표본 수 | 측정 방법 | 잠정 통과·중단 기준 | 현재 상태 |
| --- | --- | --- | --- | --- |
| 유료 의향 | 구매 결정 권한이 있는 서로 다른 시설 운영자 10명(시설 10곳) | 동일한 월 30,000원 서비스 범위와 미확인 처리 정책을 설명한 뒤, 해당 가격으로 구매할 의향과 조건을 구조화 인터뷰로 기록한다. 문의 감소 편익의 기대와 근거도 묻는다. 계약·결제로 간주하지 않는다. | 명시적 구매 의향 6/10 이상이면 통과, 2/10 이하면 중단, 3~5/10이면 수정 조사. | 미측정·미모집, 구매 의향·편익 모두 가설. |
| 시설별 정보 유지 시간 | 동의한 시설 10곳을 3개월 추적(30시설·월) | 최초 확인은 따로 타이머로 기록하고, 변경 접수·재확인·미확인 처리의 실제 서비스 측 작업 분을 합산한다. 운영자 응답 작업 시간도 별도 기록한다. 최초 확인이 2시간 이내인지 함께 확인한다. | 최초 확인 시설당 평균 ≤ 2시간이고 반복 유지 평균 ≤ 0.50시간/시설·월이면 통과. 최초 확인 평균 > 4시간 또는 반복 유지 평균 ≥ 1.50시간/시설·월이면 중단. 그 사이는 원가 재산정. | 미측정·미모집, 비용표는 실측 원가가 아니다. |
| 목적지 재검색 여부 | 낯선 시설 방문 과제를 수행할 운전자 20명(루프박스 10명·전기차 10명, 중복 없이 모집), 각 2과제 | 실제 Pleos 런타임 검증 이후 별도 승인 범위에서 실시한다. 유사 난도의 서로 다른 시설 과제를 기존 방식과 앱 사용 방식에 배정하고 순서를 절반씩 바꾼다. 목적지 설정부터 입구 선택까지 동일 목적지를 별도 검색한 여부를 관찰·기록한다. 실제 목적지 수신·선택·경로 요청 결과 로그를 함께 확인한다. 각 방식 20과제의 재검색률 차이를 비교한다. | 기존 방식 대비 앱의 재검색률이 20%p 이상 낮으면 통과, 감소가 0%p 이하이면 중단, 0%p 초과~20%p 미만이면 추가 조사. 미확인·제한 입구 선택이나 경로 요청 실패를 성공으로 집계하지 않고 별도 보고한다. | 미측정·미모집. 실제 Pleos 신호에 따른 사용자 효과는 향후 런타임 관측 대상. 합성 시연·fake 테스트·unsigned CI를 이 지표의 측정 결과로 사용하지 않는다. |

이 절은 검증 계획만 추가한다. 조사 완료·제휴 확보·제출 완료 실적을 주장하지 않으며, 기존 후보별 검증 상태와 제출 단계의 증거 범위는 아래 기록을 유지한다.

## 후보별 검증 상태

근거 수령일: **2026-11-08**. 아래 CI 수치는 사용자 요청에 전달된 검토 보고(acceptedReview)의 결과를 인용한 것이다. 별도 보고 원문·Actions 로그·아티팩트를 이번 작업에서 직접 열거나 재검증하지 않았다. 소스 확인과 제공 보고 인용은 실제 런타임 관측과 구분한다.

| 후보 SHA | Actions 실행 번호 | 보고된 작업 결과 | 보고된 테스트 집계 | 보고된 unsigned APK SHA-256 |
| --- | --- | --- | --- | --- |
| `1b3e2716204f023c2adabdec7d4839be3503b074` | `42481652866` | `testPleosDebugUnitTest` 성공 · `assemblePleosDebug` 성공 | 테스트 13건 · 실패 0건 · 오류 0건 · 건너뜀 0건 | `f4106e97cb738915a01736bd93fb00eb32dd66642e5b1a448e1ed81d034a3e0f` |

이 기록은 위 후보에 한정한다. 문서 정정을 포함한 이후 변경 HEAD는 별도 검증 대상이며, 후보 CI 성공으로 그 HEAD의 성공을 보증하지 않는다. 2026-11-08 당시 로컬 시도에서 Gradle·Java 도구를 찾지 못한 기록은 과거 실행 환경의 기록이며 후보 CI 결과를 부정하지 않는다.

| 검증 층위 | 근거와 상태 (2026-11-08 수령·소스 확인) | 제출 단계에 쓰는 범위 |
| --- | --- | --- |
| 합성 목적지·등록 차량값에 따른 판단 | `SampleTrip.SimulatedNavigation`이 코엑스 시연 목적지를 전달한다. 현재 차량은 AAOS 조회 대상이며, JVM 검사는 합성 차량 입력을 사용한다. 방문 조건은 화면의 요금 가정이고 시설 공개값과 가상 대안을 혼합한다. 소스상 판단 구현을 확인했으며 실제 차량 신호 관측은 아니다. | 제안서에 구현 범위와 시연값을 설명할 수 있다. 사용자 결과의 실측 증거는 아니다. |
| fake 내비 계약 | `ParkingNavigationTest`가 최신 목적지, 확인 전 0건·확인 후 1건, 실패 구분, 입구 되울림 시 원래 방문지 유지를 검사한다. 실행 통과 근거는 위 후보의 보고된 JVM 테스트 결과다. | 프로토타입 계약 설명에 사용한다. 실제 SDK 콜백 증거는 아니다. |
| unsigned debug CI | 위 후보의 테스트·빌드 성공과 APK 해시를 제공 보고에서 인용했다. 이번 작업의 직접 실행·해시 검증은 없다. | 선택 GitHub/APK 자료의 빌드 근거이며 제출 적격성·설치 가능성을 확정하지 않는다. |
| 실제 Pleos 런타임·사용자 결과 | 실제 목적지 신호 수신 → 재검색 없이 주차장 선택 → 실제 경로 요청 결과의 관측은 미확보다. 시연 어댑터는 `SIMULATED`를 반환하며 SDK를 호출하지 않는다. | 최종 PT 작동 앱 시연에 필요한 실제 연동 증거는 별도로 준비해야 한다. |
| 설치·환경·릴리스 | Windows 에뮬레이터 설치·화면, 차량, 음성·음향 시험 및 서명 릴리스·공개 배포 증거는 미확보다. Windows·실차·공개 배포는 별도 승인 범위다. | CI 성공만으로 최종 시연 준비 완료나 릴리스 검증을 주장하지 않는다. |

## 제출 단계와 요구 자료

출처: **사용자 제공 요건**, 수령일 **2026-11-08**. 사용자 제공 심사 URL: [심사·시상 안내](https://appdevchallenge.auto-hmg.io/guide/judging-awards). 이번 작업에서는 웹 재확인을 하지 않았으며 아래는 제공 요건의 정리다.

| 단계 | 제공 요건 | 현재 문서에서 확인할 수 있는 범위 |
| --- | --- | --- |
| 1차 필수 | 제안서와 서명 동의서 두 종류 | 이 문서는 기획 초안이다. 완성 제안서·서명 동의서 확보나 제출 완료를 주장하지 않는다. |
| 1차 선택 | 프로토타입 및 요건을 충족하는 GitHub/APK | 위 후보의 보고된 unsigned debug CI 근거만 기록한다. 제출물 존재·요건 충족·unsigned APK의 제출 적격성은 확정하지 않는다. |
| 최종 PT 필수 | 작동 앱 시연 | 합성 입력·fake 계약·unsigned 빌드와 구분한다. 실제 연동·설치·시연 관측은 미확보이며 작동 시연 완료를 주장하지 않는다. |

## 현재 판단·조건별 기대 결과 (소스 대조일 2026-11-08)

최초 시작은 **120분·구매 할인 미적용**이다. 차량은 `onStart`에서 UNKNOWN으로 초기화한 뒤 AAOS에서 다시 조회한다. 고정 차량 높이나 비전기차 기본값을 넣지 않는다. `ParkingPlan.compare`의 기본 정렬은 요청 가능 여부 → Fit → 부설 우선 → 예상 요금 순이다. 상세의 추천 기준에서 요금 우선을 고르면 요청 가능한 후보만 예상 요금 오름차순(미확인 마지막), 동률 부설 우선, 나머지 동률 입력 순서로 정렬한다. 요청 불가 후보의 판정과 기존 순서는 유지한다. 입구 누락은 명시적 입차 금지가 아니라 “입구 정보 미확인”이다. `candidateSummary`의 후보 수·빈 목록 문구는 JVM 테스트 대상이며 현재 `MainActivity.refresh`는 이를 화면에 표시하지 않는다. 화면 상태는 조회 중 “차량 정보 확인 중”, 조회 종료 후 높이 누락이면 “차량 높이 확인 필요”, 높이는 있지만 요청 가능 0건이면 “입구 안내 가능한 주차장 없음”이다.

아래 표는 두 문서에 동일하게 적용한다. 합성 행의 수치는 기존 입력·요금식·assertion을 대조한 계산 결과이며 화면의 후보 수 표시나 실제 차량 신호 관측을 뜻하지 않는다. 원본 차량 속성·연료를 바꾸는 수동 조작은 없다. 현재는 상세에서 사용자 확인 총높이를 별도로 보완할 수 있다.

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
| 보완 후 화면 종료 → 새 onStart | 보완값 삭제 후 차량 재조회. 이전 세션 입력창 적용·삭제 차단. Bundle은 체류 시간만 저장하며 보완값 복원 없음 (같은 세션 테스트와 MainActivity 수명주기 대조) |
| 적용·설명 갱신 → 이전 선택 확인 → 새 대안 A 선택 확인 | 갱신 자체 요청 0건, 이전 선택 STALE. 새 선택 확인만 원래 방문지와 37.51400, 127.06100으로 1건. 중복 확인 추가 요청 없음. fake REJECTED는 실패로 기록 (`hardwareSupplementFlowsThroughExplanationAndConfirmedNavigation`) |

원본 높이·기존 출처, 사용자 확인 총높이, 판단 적용 높이는 상세에만 구분한다. 첫 화면은 기존 대안 이유 최대 한 줄을 유지한다. 삭제·적용은 refresh에서 후보·설명을 함께 갱신하고 선택을 만료시킨다. 새 테스트는 기존과 같은 `app/src/test/java`에 있어 `testPleosDebugUnitTest` 대상이다. 위 assertion 및 소스 경로 대조는 실행 통과 증거가 아니다.

이번 작업 환경 PATH에 Java·javac·Gradle이 없어 테스트·unsigned APK 빌드는 실행하지 못했다. 도구 설치·네트워크·CI 호출·커밋·푸시는 수행하지 않았다. 새 후보 SHA와 CI 아티팩트가 제공되지 않아 수용 항목 8은 대기다. Oracle의 새 커밋 후 GitHub Actions에서 `testPleosDebugUnitTest`·`assemblePleosDebug`, 신규 VehicleHeightTest 및 기존 ParkingNavigationTest 등 XML 실패·오류·건너뜀 0을 확인해야 한다. `source-sha.txt`와 `pleos-build-<full SHA>`가 새 전체 SHA와 일치해야 하며 README의 두 sha256sum 검사, 입력·APK 해시, Gradle 로그, APK 패키지·ZIP·unsigned 검사를 확인한다. 기준 후보 성공으로 대체하지 않는다. 커밋 후 `git diff --check 1cbfb84534174c5d459131be2c52581939d66cc7 HEAD` 및 같은 범위의 지정 6개 경로 제한도 확인해야 한다.

1차 제안서·서명 동의서 필수와 최종 PT 작동 앱 시연 필수 구분을 유지한다. 외부 자료는 기존 출처·확인일을 따르며 이번에는 재확인하지 않았다. Windows 설치·화면·수명주기, 실제 SDK 콜백·차량 신호·실차·음성·음향 증거는 미확보다.


### 방문별 요금 조건: fake 합성 검증

새 목적지를 받으면 체류 시간은 유지하고 구매 할인 가정은 미적용으로 초기화한다. 사용자는 새 방문의 할인 자격을 다시 확인해야 한다. `ParkingNavigation.visitVersion`은 상관 요청 ID로 식별된 입구 되울림을 제외한 목적지 이벤트마다 증가한다. ID·이름이 같은 이벤트도 새 방문이다. 선택 만료 generation과 달리 일반 refresh·선택 만료·확인은 방문 버전을 바꾸지 않아 같은 방문의 할인은 유지된다.

`VisitQuoteStateTest`의 재현 순서와 기대값(실제 SDK 관측 아님): 합성 AAOS 1850mm·NON_ELECTRIC·120분으로 fake 방문 A 수신 → 요금창 할인 적용 → 코엑스 0원 → 같은 SampleTrip 시설의 방문 B 수신 → 할인 false·120분 유지·코엑스 12,000원·대안 A 6,000원. A의 요금창 토큰으로 60분·할인을 적용해도 B의 두 조건은 바뀌지 않는다. B에서 새로 연 창은 현재 할인 상태를 표시하고 적용 가능하며 취소는 값을 쓰지 않는다. 창 제목은 방문지, 설명은 현재 방문에만 적용됨과 할인 미적용/적용 가정을 표시한다. 이전 창의 적용은 토큰 검사 후 닫히며 새 방문 조건을 덮어쓰지 않는다.

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
