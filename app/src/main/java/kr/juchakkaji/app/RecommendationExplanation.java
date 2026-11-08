package kr.juchakkaji.app;

import java.util.List;

/** Presentation only: preserves the supplied order, fit and request contract. */
record RecommendationExplanation(String headline, String vehicleDetails, String candidateSummary) {
    static RecommendationExplanation create(VehicleProfile vehicle, boolean reading,
                                             List<ParkingPlan.Option> options) {
        return create(vehicle, new VehicleHeight(), reading, options);
    }

    static RecommendationExplanation create(VehicleProfile vehicle, VehicleHeight heightInput,
                                             boolean reading, List<ParkingPlan.Option> options) {
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
        if (vehicle.catalogHeight()) details += "\n높이 출처 " + VehicleProfile.CATALOG_SOURCE;
        details += "\n사용자 확인 총높이 " + (heightInput.confirmedMm() == null
                ? "미입력" : heightInput.confirmedMm() + "mm · 사용자 직접 확인값")
                + "\n판단 적용 높이 " + (heightInput.effectiveMm(vehicle) == null
                ? "미확인" : heightInput.effectiveMm(vehicle) + "mm");
        String headline = "";
        if (!options.isEmpty() && options.get(0).requestAvailability().available()
                && !options.get(0).lot().attached()) {
            // Explain the first unavailable attached option, without reevaluating its rules.
            for (ParkingPlan.Option option : options) {
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
