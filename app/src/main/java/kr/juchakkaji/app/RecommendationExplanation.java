package kr.juchakkaji.app;

import java.util.List;

/** Presentation only: preserves the supplied order, fit and request contract. */
record RecommendationExplanation(String headline, String vehicleDetails, String candidateSummary) {
    /** The recommendation card shows the facility and one reason; headline keeps all reasons. */
    String cardHeadline() {
        String[] parts = headline.split(" · ", 3);
        return parts.length < 3 ? headline : parts[0] + " · " + parts[1];
    }

    static RecommendationExplanation create(VehicleProfile vehicle, boolean reading,
                                             List<ParkingPlan.Option> options) {
        return create(vehicle, new VehicleHeight(), reading, options);
    }

    static RecommendationExplanation create(VehicleProfile vehicle, VehicleHeight heightInput,
                                             boolean reading, List<ParkingPlan.Option> options) {
        return create(vehicle, heightInput, reading, options, ParkingPlan.Priority.ATTACHED_FIRST);
    }

    static RecommendationExplanation create(VehicleProfile vehicle, VehicleHeight heightInput,
                                             boolean reading, List<ParkingPlan.Option> options,
                                             ParkingPlan.Priority priority) {
        if (reading) return new RecommendationExplanation("", "차량 정보 확인 중", "차량 정보 확인 중");
        String height = vehicle.heightMm() == null ? "확인 필요"
                : vehicle.heightMm() + "mm · "
                    + (vehicle.catalogHeight() ? "제조사 기본 제원" : "차량 속성값");
        String fuel = switch (vehicle.drive()) {
            case ELECTRIC -> "전기차";
            case NON_ELECTRIC -> "비전기차";
            case NEEDS_CHECK -> "확인 필요";
        };
        String details = "차량 높이 " + height + "\n연료 유형 " + fuel;
        details += "\n차량 너비 " + (vehicle.widthMm() == null || vehicle.widthMm() <= 0
                ? "확인 필요" : vehicle.widthMm() + "mm · "
                + (vehicle.catalogWidth() ? "제조사 기본 제원" : "차량 속성값"));
        if (vehicle.catalogWidth()) details += "\n너비 출처 " + VehicleProfile.CATALOG_SOURCE;
        if (vehicle.catalogHeight()) details += "\n높이 출처 " + VehicleProfile.CATALOG_SOURCE;
        details += "\n사용자 확인 총높이 " + (heightInput.confirmedMm() == null
                ? "미입력" : heightInput.confirmedMm() + "mm · 사용자 직접 확인값")
                + "\n판단 적용 높이 " + (heightInput.effectiveMm(vehicle) == null
                ? "미확인" : heightInput.effectiveMm(vehicle) + "mm");
        details += "\n추천 기준 " + (priority == ParkingPlan.Priority.FARE_FIRST ? "요금 우선" : "부설 우선");
        String headline = "";
        if (!options.isEmpty() && options.get(0).requestAvailability().available()
                && !options.get(0).lot().attached()) {
            if (priority == ParkingPlan.Priority.FARE_FIRST && options.get(0).expectedWon() != null
                    && options.stream().anyMatch(option -> option.lot().attached()
                    && option.requestAvailability().available())) {
                boolean knownAttachedFare = options.stream().anyMatch(option -> option.lot().attached()
                        && option.requestAvailability().available() && option.expectedWon() != null);
                headline = !knownAttachedFare
                        ? "요금 우선 · 요금 확인된 대안"
                        : "요금 우선 · 예상 요금이 낮은 대안";
            }
            // Explain the first unavailable attached option, without reevaluating its rules.
            for (ParkingPlan.Option option : options) {
                if (!headline.isEmpty()) break;
                if (option.lot().attached() && !option.requestAvailability().available()) {
                    String reason = option.requestAvailability().reason()
                            .replace("표기 높이 제한 초과", "높이 초과")
                            .replace("전기차 입차 조건 확인 필요", "전기차 입차 조건 미확인");
                    headline = option.lot().name() + " · " + reason;
                    break;
                }
            }
        }
        return new RecommendationExplanation(headline, details, ParkingPlan.candidateSummary(options));
    }
}
