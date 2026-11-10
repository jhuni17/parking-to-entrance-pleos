package kr.juchakkaji.app;

import java.util.List;

/** Read-only presentation of a past request against already computed current options. */
record RequestRecheck(ParkingNavigation.Receipt receipt, State state, String reason) {
    enum State { AVAILABLE, RECHECK, READING, UNKNOWN, EXCLUDED }

    static RequestRecheck create(ParkingNavigation.Receipt receipt, boolean reading,
                                 List<ParkingPlan.Option> options) {
        if (receipt == null) return null;
        if (reading) return new RequestRecheck(receipt, State.READING, "차량 정보 확인 중");
        for (ParkingPlan.Option option : options) {
            if (option.lot() != receipt.lot()) continue;
            ParkingPlan.RequestAvailability availability = option.requestAvailability();
            return new RequestRecheck(receipt, option.excluded() ? State.EXCLUDED
                    : availability.available() ? State.AVAILABLE : State.RECHECK, availability.reason());
        }
        return new RequestRecheck(receipt, State.UNKNOWN, "이전 시설 정보 미확인");
    }

    boolean needsRecheck() { return state != State.AVAILABLE; }

    String summary() {
        String text = needsRecheck() ? "이전 요청 · 입구 재확인 필요"
                : receipt.request().entrance().label() + " · " + outcomeText();
        return needsRecheck() && receipt.outcome() == ParkingNavigation.Status.REJECTED
                ? text + " · 입구 경로 요청 실패" : text;
    }

    String outcomeText() {
        return switch (receipt.outcome()) {
            case SIMULATED -> "시연 요청 완료";
            case PENDING -> "내비 응답 기다리는 중";
            case ACCEPTED -> "내비 요청 수락";
            case REJECTED -> "입구 경로 요청 실패";
            case STALE -> "만료된 선택";
            case IDLE -> "입구 요청 전";
        };
    }

    String currentText() {
        return "현재 요청 가능 여부: " + (state == State.AVAILABLE ? "요청 가능"
                : state == State.READING ? "판단 유보" : "입구 재확인 필요") + "\n현재 사유: " + reason;
    }
}
