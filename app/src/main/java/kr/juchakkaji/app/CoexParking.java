package kr.juchakkaji.app;

import java.util.List;

import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.data.RouteInfo;

import static kr.juchakkaji.app.ParkingPlan.*;

/** Curated, single-campus data for the first live navigation integration. */
final class CoexParking {
    private static final Tariff COEX_FARE = new Tariff(15, 1500, 120, "대상 매장 10만 원 이상 구매 시 2시간 무료");
    // Captured from the Pleos NaviHelper destination for the signed northern entrance.
    private static final Entrance COEX_ENTRANCE = new Entrance("북문 GATE 4", 37.51345555, 127.059225);
    static final Lot EMULATOR_ALTERNATIVE = new Lot("대안 A · 시연 주차장", "공영 노외 예시",
            false, 2500, EvEntry.ALLOWED, new Tariff(10, 500, 0, ""), null,
            "높이·요금·전기차 입차 조건은 에뮬레이터 시연값입니다. 실제 입구 안내는 제공하지 않습니다.");

    static List<Lot> lotsFor(String name, double latitude, double longitude, boolean emulatorTest) {
        String normalized = name.replace(" ", "").toLowerCase(java.util.Locale.ROOT);
        if (!(normalized.equals("코엑스") || normalized.equals("coex")
                || normalized.equals("코엑스컨벤션센터")
                || normalized.equals("코엑스몰") || normalized.equals("스타필드코엑스몰")
                || normalized.equals("coexmall")) || Math.abs(latitude - 37.512) > 0.004
                || Math.abs(longitude - 127.059) > 0.004) return List.of();
        Lot coex = new Lot("코엑스 지하주차장", emulatorTest ? "부설 · 지하 · 경로 시험" : "부설 · 지하",
                true, 2200, emulatorTest ? EvEntry.ALLOWED : EvEntry.UNKNOWN, COEX_FARE, COEX_ENTRANCE,
                "코엑스 공식 안내: 높이·요금·구매 할인 (2026-11-10 확인)\n"
                        + "Pleos 기본 내비: 주차장 POI의 차량 안내점 (2026-11-10 확인)\n"
                        + (emulatorTest ? "전기차 입차 허용은 에뮬레이터 경로 시험용 가정"
                        : "전기차 입차 조건 미확인"));
        return emulatorTest ? List.of(coex, EMULATOR_ALTERNATIVE) : List.of(coex);
    }

    static RouteInfo route(Entrance entrance) {
        if (!COEX_ENTRANCE.equals(entrance)) return null;
        return new RouteInfo(entrance.latitude(), entrance.longitude(), null, null,
                "코엑스 GATE4(북문주차장)", "36khp5g3dn76cm5nqhe3", "서울특별시 강남구 영동대로 513",
                "0", RouteOption.RECOMMENDED, "", false);
    }

    private CoexParking() {}
}
