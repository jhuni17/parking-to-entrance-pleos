package kr.juchakkaji.app;

import java.util.Arrays;
import java.util.List;

import static kr.juchakkaji.app.ParkingPlan.*;

final class SampleTrip {
    static final String VISIT = "스타필드 코엑스몰";
    static final String HEIGHT_SOURCE =
            "https://business.coex.co.kr/guide/conference-room-parking-information/general-vehicle/";
    static final String PRICE_SOURCE = "https://www.coex.co.kr/guide/parking-information/fee/";

    static final List<Lot> LOTS = Arrays.asList(
            new Lot("코엑스 지하주차장", "부설 · 지하", true, 2200, EvEntry.UNKNOWN,
                    new Tariff(15, 1500, 120, "대상 매장 영수증 10만 원 이상 구매 시 2시간 무료"),
                    new Entrance("코엑스 입구 (시연 좌표)", 37.51200, 127.06000),
                    "높이·승용차 요금·10만 원 할인: 코엑스 공식 안내 (2026-11-08 확인)\n"
                            + "입구 좌표: 시연값 · 전기차 입차 조건: 미확인"),
            new Lot("대안 A · 시연 주차장", "공영 노외 예시", false, 2500, EvEntry.ALLOWED,
                    new Tariff(10, 500, 0, ""), new Entrance("대안 A 입구 (시연 좌표)", 37.51400, 127.06100),
                    "높이·요금·전기차 조건·입구 좌표: 가상 시연값"),
            new Lot("대안 B · 시연 주차장", "민영 예시", false, null, EvEntry.UNKNOWN,
                    new Tariff(30, 1000, 0, ""), null, "요금: 가상 시연값 · 높이와 입구 정보 없음"));

    /** Explicit fixture: no SDK calls and no claim that guidance started. */
    static final class SimulatedNavigation implements NavigationPort {
        private java.util.function.Consumer<Destination> listener;
        public void setDestinationListener(java.util.function.Consumer<Destination> listener) {
            this.listener = listener;
        }
        void start() { listener.accept(new Destination("coex-demo", VISIT, LOTS, null)); }
        public Result requestEntrance(long requestId, RouteRequest request) {
            return Result.SIMULATED;
        }
    }

    private SampleTrip() {}
}
