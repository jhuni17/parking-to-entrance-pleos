package kr.juchakkaji.app;

/** Read-only presentation of the current query; never computes fit or schedules work. */
final class VehicleReadExplanation {
    static String details(VehicleRefreshState state, VehicleHeight height) {
        String reason = switch (state.outcome()) {
            case IDLE -> "차량 조회 진행 안 함";
            case READING -> "차량 정보 확인 중";
            case CONNECTED -> "차량 조회 응답 수신";
            case DISCONNECTED -> "차량 서비스 미연결";
            case UNAVAILABLE -> "차량 정보 읽기 불가";
            case EXPIRED -> "차량 조회 대기 만료";
        };
        String text = "조회 상태: " + reason;
        if (state.outcome() == VehicleRefreshState.Outcome.EXPIRED) {
            text += "\n3000ms는 앱 대기 정책이며 API 강제 종료나 서비스 회복 보장이 아닙니다.";
        }
        text += "\n응답 수신과 개별 차량값 확인·입차 가능 여부는 별개입니다.";
        if (!state.reading()) text += "\n차량 정보 다시 확인 버튼으로 명시적으로 재조회할 수 있습니다.";
        if (height.effectiveMm(state.vehicle()) == null) {
            text += "\n적용 높이 미확인: 직접 확인한 총높이가 있을 때만 총높이 보완을 적용하세요."
                    + " 확인값이 없으면 입구 요청을 보류하세요.";
        } else if (height.confirmedMm() != null) {
            text += "\n사용자 확인 총높이는 별도 보완값이며 차량 조회 성공을 뜻하지 않습니다.";
        }
        return text;
    }

    private VehicleReadExplanation() {}
}
